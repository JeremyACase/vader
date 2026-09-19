use crate::assignment::{Assignment, AssignmentId};
use crate::budget::HarnessBudget;
use crate::control_plane::ControlPlane;
use crate::error::HarnessError;
use crate::inference_gateway::{ConversationMessage, InferenceGateway, InferenceTurn, ToolCall};
use crate::stall_detector::StallDetector;
use crate::tool_executor::ToolExecutor;

/// The terminal result of one harness run, reported back to `core-server` via
/// [`ControlPlane::submit_result`].
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum HarnessOutcome {
    Succeeded { output: String },
    Failed { reason: String },
    TimedOut,
    Stalled,
}

/// What one completed turn means for the run as a whole: either the model produced its final
/// answer, or it requested tools and the run should take another turn with their results folded
/// in.
enum TurnOutcome {
    Finished(String),
    Continuing,
}

/// Frames the action-loop protocol for the model: call tools as needed, and a plain-text reply
/// with no further tool calls is what ends the run. Without this, a model has no way to know that
/// declining to call a tool is itself the signal to stop -- it would otherwise be indistinguishable
/// from simply not having tried yet.
///
/// Explicitly forbids asking a clarifying question as the final reply: nothing in this loop ever
/// reads a response back to the model, so a question is never answered -- it just gets reported
/// as though it were the finished result. The user message that follows always carries the
/// background (the original request, any attached files, and any prerequisite tasks' results)
/// that a lone task description wouldn't otherwise include, specifically so the model has what it
/// needs to avoid needing to ask in the first place.
const TASK_INSTRUCTIONS: &str = "You are an autonomous agent completing one task from a larger \
    workflow. No human is available to answer follow-up questions during this run -- you must \
    gather anything you need yourself, using your available tools, rather than asking a \
    clarifying question. The next message gives you the task plus background: the original \
    request, any files attached to it, and the results of any prerequisite tasks. If a file is \
    mentioned, do not assume you already know what it contains: create a Python sandbox, stage \
    the file into it with stage_object, then inspect it with run_python_code -- reserve \
    get_object_content for small text content you need to read directly, and never use it just \
    to hand a file's bytes to run_python_code, since that inlines the raw content into this \
    conversation instead of the sandbox's workspace. When you have fully completed the task, reply \
    with your final answer as plain text and do not call any more tools; that reply is what ends \
    this run and is treated as your finished result, not a question. Never end the run by asking \
    a question or requesting clarification -- if something is genuinely still missing after using \
    your tools, state your best attempt and explain what was missing instead.";

/// Drives the turn loop for exactly one [`crate::assignment::Assignment`]: call the inference
/// gateway a turn at a time -- invoking any tool calls it requests via [`ToolExecutor`] and
/// folding the results back into the conversation -- heartbeat and check the budget/stall
/// detector after each turn, then submit the terminal outcome.
///
/// Generic over all three collaborators (rather than `dyn ControlPlane` / `dyn InferenceGateway`
/// / `dyn ToolExecutor`) -- the harness has a small, closed set of implementations (the real
/// [`crate::core_server_adapter::CoreServerAdapter`], which implements all three, and test
/// doubles), so static dispatch keeps the binary small and avoids boxing every call.
pub struct AgentHarnessRunner<C: ControlPlane, G: InferenceGateway, T: ToolExecutor> {
    control_plane: C,
    inference_gateway: G,
    tool_executor: T,
    budget: HarnessBudget,
    stall_detector: StallDetector,
}

impl<C: ControlPlane, G: InferenceGateway, T: ToolExecutor> AgentHarnessRunner<C, G, T> {
    pub fn new(
        control_plane: C,
        inference_gateway: G,
        tool_executor: T,
        budget: HarnessBudget,
        stall_detector: StallDetector,
    ) -> Self {
        Self {
            control_plane,
            inference_gateway,
            tool_executor,
            budget,
            stall_detector,
        }
    }

    /// Runs the assignment to completion (success, failure, timeout, or stall) and reports the
    /// outcome to the control plane before returning it. Takes the already-fetched work order
    /// rather than fetching it itself, so a caller that also needs it up front (e.g. to size the
    /// budget from the assignment's own bounds) doesn't pay for a second round trip.
    ///
    /// Each iteration takes one inference turn against the running conversation (seeded with the
    /// assignment's context and objective): a turn with no tool calls is the model's final
    /// answer and ends the run;
    /// a turn requesting tools has each one invoked via [`ToolExecutor`], with the calls and their
    /// results folded back into the conversation for the next turn. The budget and stall-detector
    /// checks below run before every turn, so a model that never converges is bounded the same way
    /// regardless of how many tool round trips it takes along the way.
    pub async fn run(&mut self, assignment: Assignment) -> Result<HarnessOutcome, HarnessError> {
        let assignment_id = assignment.assignment_id;
        let mut messages = vec![
            ConversationMessage::system(TASK_INSTRUCTIONS),
            ConversationMessage::user(format!(
                "{}\n\nYour task: {}",
                assignment.context, assignment.objective
            )),
        ];

        let outcome = loop {
            if self.budget.is_exhausted() {
                break HarnessOutcome::TimedOut;
            }
            if self.stall_detector.is_stalled() {
                break HarnessOutcome::Stalled;
            }
            match self.take_turn(assignment_id, &mut messages).await {
                Ok(TurnOutcome::Finished(output)) => break HarnessOutcome::Succeeded { output },
                Ok(TurnOutcome::Continuing) => {}
                Err(error) => {
                    break HarnessOutcome::Failed {
                        reason: error.to_string(),
                    }
                }
            }
        };

        self.control_plane
            .submit_result(assignment_id, &outcome)
            .await?;
        Ok(outcome)
    }

    /// Takes one inference turn and, if it requested tools, invokes them and appends the exchange
    /// to `messages` -- never propagates an error out of `run` itself; the caller turns any `Err`
    /// here into a reported [`HarnessOutcome::Failed`].
    async fn take_turn(
        &mut self,
        assignment_id: AssignmentId,
        messages: &mut Vec<ConversationMessage>,
    ) -> Result<TurnOutcome, HarnessError> {
        let turn = self
            .inference_gateway
            .complete_turn(assignment_id, messages)
            .await?;
        self.budget.record_turn(turn.tokens_spent);
        self.stall_detector.record(fingerprint_of(&turn));
        self.heartbeat(assignment_id).await;

        let outcome = if turn.tool_calls.is_empty() {
            TurnOutcome::Finished(turn.content.unwrap_or_default())
        } else {
            self.append_tool_exchange(assignment_id, messages, turn.tool_calls)
                .await?;
            TurnOutcome::Continuing
        };
        Ok(outcome)
    }

    /// Invokes every tool call a turn requested, in order, and appends the assistant's request
    /// plus each tool's result to `messages` so the next turn sees them.
    async fn append_tool_exchange(
        &self,
        assignment_id: AssignmentId,
        messages: &mut Vec<ConversationMessage>,
        tool_calls: Vec<ToolCall>,
    ) -> Result<(), HarnessError> {
        let mut tool_results = Vec::with_capacity(tool_calls.len());
        for tool_call in &tool_calls {
            let result = self
                .tool_executor
                .invoke_tool(assignment_id, tool_call)
                .await?;
            tool_results.push(ConversationMessage::tool_result(
                result.tool_call_id,
                tool_call.name.clone(),
                result.result_json,
            ));
        }
        messages.push(ConversationMessage::assistant_tool_calls(tool_calls));
        messages.extend(tool_results);
        Ok(())
    }

    /// Reports liveness and progress. A failure here is logged and otherwise ignored: it's a
    /// liveness signal, not the outcome itself.
    async fn heartbeat(&self, assignment_id: AssignmentId) {
        let heartbeat = self
            .control_plane
            .heartbeat(
                assignment_id,
                self.budget.turns_used(),
                self.budget.tokens_used(),
            )
            .await;
        if let Err(error) = heartbeat {
            eprintln!("heartbeat failed (continuing): {error}");
        }
    }
}

/// The stall detector's fingerprint for one turn: the tool call(s) it requested (name +
/// arguments), or its final text when it made none. Two turns that both call the same tool with
/// the same arguments -- or both answer with the same text -- fingerprint identically.
fn fingerprint_of(turn: &InferenceTurn) -> String {
    if turn.tool_calls.is_empty() {
        return turn.content.clone().unwrap_or_default();
    }
    turn.tool_calls
        .iter()
        .map(|call| format!("{}({})", call.name, call.arguments_json))
        .collect::<Vec<_>>()
        .join(";")
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::assignment::TaskId;
    use crate::inference_gateway::ConversationRole;
    use crate::tool_executor::ToolResult;
    use std::cell::RefCell;
    use std::collections::VecDeque;
    use std::time::{Duration, Instant};
    use uuid::Uuid;

    fn assignment_id() -> AssignmentId {
        AssignmentId(Uuid::from_u128(1))
    }

    fn assignment() -> Assignment {
        Assignment {
            task_id: TaskId(Uuid::from_u128(2)),
            assignment_id: assignment_id(),
            objective: "summarize the uploaded spreadsheet".to_string(),
            context: "Original request from the user:\nAnalyze the attached file.".to_string(),
            max_turns: 20,
            max_tokens: 200_000,
            deadline_seconds: 600,
        }
    }

    fn budget() -> HarnessBudget {
        HarnessBudget::new(20, 200_000, Instant::now() + Duration::from_secs(600))
    }

    struct StubControlPlane {
        heartbeats: RefCell<u32>,
        submitted: RefCell<Option<HarnessOutcome>>,
    }

    impl StubControlPlane {
        fn new() -> Self {
            Self {
                heartbeats: RefCell::new(0),
                submitted: RefCell::new(None),
            }
        }
    }

    impl ControlPlane for StubControlPlane {
        async fn fetch_assignment(
            &self,
            _assignment_id: AssignmentId,
        ) -> Result<Assignment, HarnessError> {
            Ok(assignment())
        }

        async fn heartbeat(
            &self,
            _assignment_id: AssignmentId,
            _turns_used: u32,
            _tokens_used: u64,
        ) -> Result<(), HarnessError> {
            *self.heartbeats.borrow_mut() += 1;
            Ok(())
        }

        async fn submit_result(
            &self,
            _assignment_id: AssignmentId,
            outcome: &HarnessOutcome,
        ) -> Result<(), HarnessError> {
            *self.submitted.borrow_mut() = Some(outcome.clone());
            Ok(())
        }
    }

    /// Returns one scripted result per call, in order, panicking if the run asks for more turns
    /// than were scripted.
    struct ScriptedInferenceGateway {
        turns: RefCell<VecDeque<Result<InferenceTurn, HarnessError>>>,
        messages_seen: RefCell<Vec<Vec<ConversationMessage>>>,
    }

    impl ScriptedInferenceGateway {
        fn new(turns: Vec<Result<InferenceTurn, HarnessError>>) -> Self {
            Self {
                turns: RefCell::new(turns.into()),
                messages_seen: RefCell::new(Vec::new()),
            }
        }

        fn succeeding(turns: Vec<InferenceTurn>) -> Self {
            Self::new(turns.into_iter().map(Ok).collect())
        }
    }

    impl InferenceGateway for ScriptedInferenceGateway {
        async fn complete_turn(
            &self,
            _assignment_id: AssignmentId,
            messages: &[ConversationMessage],
        ) -> Result<InferenceTurn, HarnessError> {
            self.messages_seen.borrow_mut().push(messages.to_vec());
            self.turns
                .borrow_mut()
                .pop_front()
                .expect("scripted gateway asked for more turns than provided")
        }
    }

    struct StubToolExecutor {
        calls: RefCell<Vec<ToolCall>>,
    }

    impl StubToolExecutor {
        fn new() -> Self {
            Self {
                calls: RefCell::new(Vec::new()),
            }
        }
    }

    impl ToolExecutor for StubToolExecutor {
        async fn invoke_tool(
            &self,
            _assignment_id: AssignmentId,
            tool_call: &ToolCall,
        ) -> Result<ToolResult, HarnessError> {
            self.calls.borrow_mut().push(tool_call.clone());
            Ok(ToolResult {
                tool_call_id: tool_call.id.clone(),
                result_json: format!("\"result for {}\"", tool_call.name),
            })
        }
    }

    fn final_turn(content: &str) -> InferenceTurn {
        InferenceTurn {
            content: Some(content.to_string()),
            tool_calls: Vec::new(),
            tokens_spent: 10,
        }
    }

    fn tool_call_turn(id: &str, name: &str, arguments_json: &str) -> InferenceTurn {
        InferenceTurn {
            content: None,
            tool_calls: vec![ToolCall {
                id: id.to_string(),
                name: name.to_string(),
                arguments_json: arguments_json.to_string(),
            }],
            tokens_spent: 5,
        }
    }

    #[tokio::test]
    async fn run_with_no_tool_calls_succeeds_after_one_turn() {
        let control_plane = StubControlPlane::new();
        let inference_gateway =
            ScriptedInferenceGateway::succeeding(vec![final_turn("the answer")]);
        let tool_executor = StubToolExecutor::new();
        let mut runner = AgentHarnessRunner::new(
            control_plane,
            inference_gateway,
            tool_executor,
            budget(),
            StallDetector::new(3),
        );

        let outcome = runner.run(assignment()).await.unwrap();

        assert_eq!(
            outcome,
            HarnessOutcome::Succeeded {
                output: "the answer".to_string()
            }
        );
        assert_eq!(*runner.control_plane.submitted.borrow(), Some(outcome));
        assert_eq!(*runner.control_plane.heartbeats.borrow(), 1);
    }

    #[tokio::test]
    async fn run_seeds_the_first_turn_with_both_context_and_objective() {
        let control_plane = StubControlPlane::new();
        let inference_gateway = ScriptedInferenceGateway::succeeding(vec![final_turn("ok")]);
        let tool_executor = StubToolExecutor::new();
        let mut runner = AgentHarnessRunner::new(
            control_plane,
            inference_gateway,
            tool_executor,
            budget(),
            StallDetector::new(3),
        );

        runner.run(assignment()).await.unwrap();

        let first_call_messages = &runner.inference_gateway.messages_seen.borrow()[0];
        let user_message = first_call_messages
            .iter()
            .find(|message| message.role == ConversationRole::User)
            .expect("a user message");
        let content = user_message.content.as_deref().unwrap_or_default();
        assert!(content.contains(&assignment().context));
        assert!(content.contains(&assignment().objective));
    }

    #[tokio::test]
    async fn run_invokes_a_requested_tool_then_succeeds_on_the_next_turn() {
        let control_plane = StubControlPlane::new();
        let inference_gateway = ScriptedInferenceGateway::succeeding(vec![
            tool_call_turn("call-1", "get_object_content", "{\"id\":\"abc\"}"),
            final_turn("here is the summary"),
        ]);
        let tool_executor = StubToolExecutor::new();
        let mut runner = AgentHarnessRunner::new(
            control_plane,
            inference_gateway,
            tool_executor,
            budget(),
            StallDetector::new(3),
        );

        let outcome = runner.run(assignment()).await.unwrap();

        assert_eq!(
            outcome,
            HarnessOutcome::Succeeded {
                output: "here is the summary".to_string()
            }
        );
        assert_eq!(runner.tool_executor.calls.borrow().len(), 1);
        assert_eq!(
            runner.tool_executor.calls.borrow()[0].name,
            "get_object_content"
        );
        // The second turn's conversation includes the tool's result.
        let second_call_messages = &runner.inference_gateway.messages_seen.borrow()[1];
        assert!(
            second_call_messages
                .iter()
                .any(|message| message.content.as_deref()
                    == Some("\"result for get_object_content\""))
        );
    }

    #[tokio::test]
    async fn run_stops_once_the_turn_budget_is_exhausted() {
        let control_plane = StubControlPlane::new();
        // Every turn requests the same tool forever -- without a budget cap this would loop
        // indefinitely.
        let turns: Vec<InferenceTurn> = (0..2)
            .map(|_| tool_call_turn("call", "list_sandboxes", "{}"))
            .collect();
        let inference_gateway = ScriptedInferenceGateway::succeeding(turns);
        let tool_executor = StubToolExecutor::new();
        let mut runner = AgentHarnessRunner::new(
            control_plane,
            inference_gateway,
            tool_executor,
            HarnessBudget::new(2, 200_000, Instant::now() + Duration::from_secs(600)),
            StallDetector::new(10),
        );

        let outcome = runner.run(assignment()).await.unwrap();

        assert_eq!(outcome, HarnessOutcome::TimedOut);
    }

    #[tokio::test]
    async fn run_stops_once_the_stall_detector_trips() {
        let control_plane = StubControlPlane::new();
        // The same tool call, with the same arguments, three turns running.
        let turns: Vec<InferenceTurn> = (0..3)
            .map(|_| tool_call_turn("call", "list_sandboxes", "{}"))
            .collect();
        let inference_gateway = ScriptedInferenceGateway::succeeding(turns);
        let tool_executor = StubToolExecutor::new();
        let mut runner = AgentHarnessRunner::new(
            control_plane,
            inference_gateway,
            tool_executor,
            budget(),
            StallDetector::new(3),
        );

        let outcome = runner.run(assignment()).await.unwrap();

        assert_eq!(outcome, HarnessOutcome::Stalled);
    }

    #[tokio::test]
    async fn run_reports_failed_when_the_inference_gateway_errors() {
        let control_plane = StubControlPlane::new();
        let inference_gateway = ScriptedInferenceGateway::new(vec![Err(
            HarnessError::InferenceUnavailable("connection refused".to_string()),
        )]);
        let tool_executor = StubToolExecutor::new();
        let mut runner = AgentHarnessRunner::new(
            control_plane,
            inference_gateway,
            tool_executor,
            budget(),
            StallDetector::new(3),
        );

        let outcome = runner.run(assignment()).await.unwrap();

        assert!(matches!(outcome, HarnessOutcome::Failed { .. }));
    }
}
