use crate::assignment::{Assignment, AssignmentId};
use crate::budget::HarnessBudget;
use crate::control_plane::ControlPlane;
use crate::conversation::{ConversationMessage, InferenceTurn, ToolCall};
use crate::error::HarnessError;
use crate::harness_outcome::HarnessOutcome;
use crate::inference_gateway::InferenceGateway;
use crate::prompts::{self, EMPTY_REPLY_NUDGE, TASK_INSTRUCTIONS};
use crate::stall_detector::StallDetector;
use crate::tool_executor::ToolExecutor;
use crate::unfinished_answer_guard::UnfinishedAnswerGuard;

/// How many times one run may be sent back for an answer that looks unfinished (see
/// [`UnfinishedAnswerGuard`]). Two covers the common "propose a fix, nudge, run it, hit another
/// error, propose again" sequence; beyond that the answer goes to core-server's evaluator as-is.
const MAX_UNFINISHED_ANSWER_NUDGES: u32 = 2;

/// What one completed turn means for the run as a whole: the model produced its final answer, it
/// requested tools (or needs a nudge) and the run should take another turn, or its reply was cut
/// off at the output token cap and is unusable.
enum TurnOutcome {
    Finished(String),
    Continuing,
    CutOff,
}

impl TurnOutcome {
    /// The run's outcome if this turn ends it; `Continuing` is the only turn that doesn't.
    fn into_harness_outcome(self) -> Option<HarnessOutcome> {
        match self {
            Self::Finished(output) => Some(HarnessOutcome::Succeeded { output }),
            Self::Continuing => None,
            Self::CutOff => Some(HarnessOutcome::cut_off()),
        }
    }
}

/// Drives the turn loop for exactly one [`crate::assignment::Assignment`]: call the inference
/// gateway a turn at a time -- invoking any tool calls it requests via [`ToolExecutor`] and
/// folding the results back into the conversation -- heartbeat and check the budget/stall
/// detector before each turn, then submit the terminal outcome.
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
    unfinished_answer_guard: UnfinishedAnswerGuard,
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
            unfinished_answer_guard: UnfinishedAnswerGuard::new(MAX_UNFINISHED_ANSWER_NUDGES),
        }
    }

    /// Runs the assignment to completion (success, failure, timeout, or stall) and reports the
    /// outcome to the control plane before returning it. Takes the already-fetched work order
    /// rather than fetching it itself, so a caller that also needs it up front (e.g. to size the
    /// budget from the assignment's own bounds) doesn't pay for a second round trip.
    pub async fn run(&mut self, assignment: Assignment) -> Result<HarnessOutcome, HarnessError> {
        let assignment_id = assignment.assignment_id;
        let mut messages = opening_messages(&assignment);
        let outcome = self.drive_to_outcome(assignment_id, &mut messages).await;
        self.report(assignment_id, &outcome).await.map(|()| outcome)
    }

    /// Takes steps until one of them ends the run. The budget and stall-detector checks run
    /// before every turn, so a model that never converges is bounded the same way regardless of
    /// how many tool round trips it takes along the way.
    async fn drive_to_outcome(
        &mut self,
        assignment_id: AssignmentId,
        messages: &mut Vec<ConversationMessage>,
    ) -> HarnessOutcome {
        loop {
            if let Some(outcome) = self.step(assignment_id, messages).await {
                break outcome;
            }
        }
    }

    /// One iteration of the loop: ends the run if a limit has tripped, otherwise takes a turn.
    async fn step(
        &mut self,
        assignment_id: AssignmentId,
        messages: &mut Vec<ConversationMessage>,
    ) -> Option<HarnessOutcome> {
        match self.limit_outcome(assignment_id) {
            Some(outcome) => Some(outcome),
            None => self.play_turn(assignment_id, messages).await,
        }
    }

    /// The outcome that ends the run because a hard limit tripped, if one has.
    fn limit_outcome(&self, assignment_id: AssignmentId) -> Option<HarnessOutcome> {
        self.budget_outcome(assignment_id)
            .or_else(|| self.stall_outcome(assignment_id))
    }

    fn budget_outcome(&self, assignment_id: AssignmentId) -> Option<HarnessOutcome> {
        self.budget.is_exhausted().then(|| {
            log::warn!("assignment {assignment_id:?}: budget exhausted, ending run");
            HarnessOutcome::TimedOut
        })
    }

    fn stall_outcome(&self, assignment_id: AssignmentId) -> Option<HarnessOutcome> {
        self.stall_detector.is_stalled().then(|| {
            log::warn!("assignment {assignment_id:?}: stall detected, ending run");
            HarnessOutcome::Stalled
        })
    }

    /// Takes one turn and says whether it ended the run. A turn that errors ends the run as
    /// [`HarnessOutcome::Failed`] rather than propagating, so the failure is still reported.
    async fn play_turn(
        &mut self,
        assignment_id: AssignmentId,
        messages: &mut Vec<ConversationMessage>,
    ) -> Option<HarnessOutcome> {
        match self.take_turn(assignment_id, messages).await {
            Ok(turn_outcome) => turn_outcome.into_harness_outcome(),
            Err(error) => Some(failed_turn(assignment_id, error)),
        }
    }

    async fn take_turn(
        &mut self,
        assignment_id: AssignmentId,
        messages: &mut Vec<ConversationMessage>,
    ) -> Result<TurnOutcome, HarnessError> {
        let turn = self.request_turn(assignment_id, messages).await?;
        self.respond_to_turn(assignment_id, messages, turn).await
    }

    /// Requests one inference turn and records it against the budget, stall detector and
    /// heartbeat.
    async fn request_turn(
        &mut self,
        assignment_id: AssignmentId,
        messages: &[ConversationMessage],
    ) -> Result<InferenceTurn, HarnessError> {
        log::debug!(
            "assignment {assignment_id:?}: requesting a turn ({} message(s) so far)",
            messages.len()
        );
        let turn = self
            .inference_gateway
            .complete_turn(assignment_id, messages)
            .await?;
        self.record_turn(assignment_id, &turn).await;
        Ok(turn)
    }

    async fn record_turn(&mut self, assignment_id: AssignmentId, turn: &InferenceTurn) {
        self.budget.record_turn(turn.tokens_spent);
        self.stall_detector.record(turn.fingerprint());
        self.heartbeat(assignment_id).await;
    }

    /// Decides what a completed turn means: unusable if cut off, a final answer (or a nudge) if
    /// it called no tools, otherwise a tool exchange to fold into the conversation.
    async fn respond_to_turn(
        &mut self,
        assignment_id: AssignmentId,
        messages: &mut Vec<ConversationMessage>,
        turn: InferenceTurn,
    ) -> Result<TurnOutcome, HarnessError> {
        if turn.was_cut_off() {
            log::warn!("assignment {assignment_id:?}: reply was cut off at the output token cap");
            Ok(TurnOutcome::CutOff)
        } else if turn.tool_calls.is_empty() {
            Ok(self.final_answer_or_nudge(assignment_id, messages, turn.content))
        } else {
            self.append_tool_exchange(assignment_id, messages, turn.tool_calls)
                .await
                .map(|()| TurnOutcome::Continuing)
        }
    }

    /// A turn with no tool calls is the model's final answer -- unless it is blank (see
    /// [`EMPTY_REPLY_NUDGE`]), in which case the model is nudged to continue instead.
    fn final_answer_or_nudge(
        &mut self,
        assignment_id: AssignmentId,
        messages: &mut Vec<ConversationMessage>,
        content: Option<String>,
    ) -> TurnOutcome {
        match content.filter(|content| !content.trim().is_empty()) {
            Some(answer) => self.finished_or_nudged(assignment_id, messages, answer),
            None => nudge_past_empty_reply(assignment_id, messages),
        }
    }

    /// Accepts a non-blank answer as final unless the guard flags it as unfinished; then the
    /// answer is replayed into the conversation, followed by the nudge, so the model sees what it
    /// said and why it was sent back.
    fn finished_or_nudged(
        &mut self,
        assignment_id: AssignmentId,
        messages: &mut Vec<ConversationMessage>,
        answer: String,
    ) -> TurnOutcome {
        match self.unfinished_answer_guard.review(&answer, messages) {
            None => finished(assignment_id, answer),
            Some(nudge) => nudge_past_unfinished_answer(assignment_id, messages, answer, nudge),
        }
    }

    /// Invokes every tool call a turn requested, in order, and appends the assistant's request
    /// plus each tool's result to `messages` so the next turn sees them.
    async fn append_tool_exchange(
        &self,
        assignment_id: AssignmentId,
        messages: &mut Vec<ConversationMessage>,
        tool_calls: Vec<ToolCall>,
    ) -> Result<(), HarnessError> {
        let tool_results = self.invoke_tool_calls(assignment_id, &tool_calls).await?;
        messages.push(ConversationMessage::assistant_tool_calls(tool_calls));
        messages.extend(tool_results);
        Ok(())
    }

    /// Invokes each tool call in order, stopping at the first that fails.
    async fn invoke_tool_calls(
        &self,
        assignment_id: AssignmentId,
        tool_calls: &[ToolCall],
    ) -> Result<Vec<ConversationMessage>, HarnessError> {
        log::debug!(
            "assignment {assignment_id:?}: model requested {} tool call(s)",
            tool_calls.len()
        );
        let mut tool_results = Vec::with_capacity(tool_calls.len());
        for tool_call in tool_calls {
            tool_results.push(self.invoke_tool_call(assignment_id, tool_call).await?);
        }
        Ok(tool_results)
    }

    /// Invokes one tool call and wraps its result as the `Tool` message that answers it.
    async fn invoke_tool_call(
        &self,
        assignment_id: AssignmentId,
        tool_call: &ToolCall,
    ) -> Result<ConversationMessage, HarnessError> {
        log::debug!(
            "assignment {assignment_id:?}: invoking tool {}",
            tool_call.name
        );
        self.tool_executor
            .invoke_tool(assignment_id, tool_call)
            .await
            .map(|result| {
                ConversationMessage::tool_result(
                    result.tool_call_id,
                    tool_call.name.clone(),
                    result.result_json,
                )
            })
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
            log::warn!("assignment {assignment_id:?}: heartbeat failed (continuing): {error}");
        }
    }

    async fn report(
        &self,
        assignment_id: AssignmentId,
        outcome: &HarnessOutcome,
    ) -> Result<(), HarnessError> {
        log::info!("assignment {assignment_id:?}: reporting outcome {outcome:?}");
        self.control_plane
            .submit_result(assignment_id, outcome)
            .await
    }
}

/// Seeds the conversation: the loop protocol, then the task with its background.
fn opening_messages(assignment: &Assignment) -> Vec<ConversationMessage> {
    vec![
        ConversationMessage::system(TASK_INSTRUCTIONS),
        ConversationMessage::user(prompts::task_message(
            &assignment.context,
            &assignment.objective,
        )),
    ]
}

fn failed_turn(assignment_id: AssignmentId, error: HarnessError) -> HarnessOutcome {
    log::error!("assignment {assignment_id:?}: turn failed: {error}");
    HarnessOutcome::Failed {
        reason: error.to_string(),
    }
}

fn finished(assignment_id: AssignmentId, answer: String) -> TurnOutcome {
    log::info!("assignment {assignment_id:?}: model produced a final answer");
    TurnOutcome::Finished(answer)
}

fn nudge_past_empty_reply(
    assignment_id: AssignmentId,
    messages: &mut Vec<ConversationMessage>,
) -> TurnOutcome {
    log::warn!("assignment {assignment_id:?}: model returned an empty reply, nudging it");
    messages.push(ConversationMessage::user(EMPTY_REPLY_NUDGE));
    TurnOutcome::Continuing
}

fn nudge_past_unfinished_answer(
    assignment_id: AssignmentId,
    messages: &mut Vec<ConversationMessage>,
    answer: String,
    nudge: &str,
) -> TurnOutcome {
    log::warn!("assignment {assignment_id:?}: final answer looks unfinished, nudging it");
    messages.push(ConversationMessage::assistant_text(answer));
    messages.push(ConversationMessage::user(nudge));
    TurnOutcome::Continuing
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::assignment::TaskId;
    use crate::conversation::ConversationRole;
    use crate::harness_outcome::OUTPUT_CAP_FAILURE_REASON;
    use crate::prompts::UNEXECUTED_CODE_NUDGE;
    use crate::tool_executor::ToolResult;
    use std::cell::RefCell;
    use std::collections::VecDeque;
    use std::time::{Duration, Instant};

    fn assignment_id() -> AssignmentId {
        AssignmentId::parse("00000000-0000-0000-0000-000000000001").unwrap()
    }

    fn assignment() -> Assignment {
        Assignment {
            task_id: TaskId::parse("00000000-0000-0000-0000-000000000002").unwrap(),
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
            finish_reason: Some("stop".to_string()),
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
            finish_reason: Some("stop".to_string()),
        }
    }

    fn cut_off_turn() -> InferenceTurn {
        InferenceTurn {
            content: Some("<tool_call>{\"name\": \"run_python_code\", \"argu".to_string()),
            tool_calls: Vec::new(),
            tokens_spent: 2048,
            finish_reason: Some("length".to_string()),
        }
    }

    #[tokio::test]
    async fn run_fails_with_a_clear_reason_when_a_reply_is_cut_off_at_the_output_cap() {
        let control_plane = StubControlPlane::new();
        let inference_gateway = ScriptedInferenceGateway::succeeding(vec![cut_off_turn()]);
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
            HarnessOutcome::Failed {
                reason: OUTPUT_CAP_FAILURE_REASON.to_string()
            }
        );
        assert_eq!(*runner.control_plane.submitted.borrow(), Some(outcome));
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

    fn empty_turn() -> InferenceTurn {
        InferenceTurn {
            content: Some("  ".to_string()),
            tool_calls: Vec::new(),
            tokens_spent: 1,
            finish_reason: Some("stop".to_string()),
        }
    }

    #[tokio::test]
    async fn run_nudges_past_an_empty_reply_instead_of_succeeding_with_no_output() {
        let control_plane = StubControlPlane::new();
        let inference_gateway =
            ScriptedInferenceGateway::succeeding(vec![empty_turn(), final_turn("the answer")]);
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
        let second_call_messages = &runner.inference_gateway.messages_seen.borrow()[1];
        let last_message = second_call_messages.last().expect("a message");
        assert_eq!(last_message.role, ConversationRole::User);
        assert_eq!(last_message.content.as_deref(), Some(EMPTY_REPLY_NUDGE));
    }

    #[tokio::test]
    async fn run_stalls_rather_than_succeeding_when_every_reply_is_empty() {
        let control_plane = StubControlPlane::new();
        let inference_gateway =
            ScriptedInferenceGateway::succeeding((0..3).map(|_| empty_turn()).collect());
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
    async fn run_nudges_past_an_answer_with_unrun_code_and_finishes_after_running_it() {
        let control_plane = StubControlPlane::new();
        let proposal = "Here's the fix:\n```python\nprint(df.describe())\n```\nLet's proceed.";
        let code_call = "{\"code\":\"print(df.describe())\"}";
        let inference_gateway = ScriptedInferenceGateway::succeeding(vec![
            final_turn(proposal),
            tool_call_turn("call-1", "run_python_code", code_call),
            final_turn("The mean agility is 0.4."),
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
                output: "The mean agility is 0.4.".to_string()
            }
        );
        let second_call_messages = &runner.inference_gateway.messages_seen.borrow()[1];
        let replayed = &second_call_messages[second_call_messages.len() - 2];
        assert_eq!(replayed.role, ConversationRole::Assistant);
        assert_eq!(replayed.content.as_deref(), Some(proposal));
        let nudge = second_call_messages.last().expect("a message");
        assert_eq!(nudge.role, ConversationRole::User);
        assert_eq!(nudge.content.as_deref(), Some(UNEXECUTED_CODE_NUDGE));
    }

    #[tokio::test]
    async fn run_accepts_an_unfinished_looking_answer_once_the_model_repeats_it() {
        let control_plane = StubControlPlane::new();
        let deliverable = "```python\ndef add(a, b):\n    return a + b\n```";
        let inference_gateway = ScriptedInferenceGateway::succeeding(vec![
            final_turn(deliverable),
            final_turn(deliverable),
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
                output: deliverable.to_string()
            }
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
