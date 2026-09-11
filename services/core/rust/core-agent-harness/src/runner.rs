use crate::assignment::AssignmentId;
use crate::budget::HarnessBudget;
use crate::control_plane::ControlPlane;
use crate::error::HarnessError;
use crate::inference_gateway::InferenceGateway;
use crate::stall_detector::StallDetector;

/// The terminal result of one harness run, reported back to `core-server` via
/// [`ControlPlane::submit_result`].
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum HarnessOutcome {
    Succeeded { output: String },
    Failed { reason: String },
    TimedOut,
    Stalled,
}

/// Drives the turn loop for exactly one [`crate::assignment::Assignment`]: fetch the work order,
/// call the inference gateway a turn at a time, heartbeat and check the budget/stall detector
/// after each turn, then submit the terminal outcome.
///
/// Generic over both collaborators (rather than `dyn ControlPlane` / `dyn InferenceGateway`) --
/// the harness has a small, closed set of implementations (the real
/// [`crate::core_server_adapter::CoreServerAdapter`] and a test double), so static dispatch
/// keeps the binary small and avoids boxing every call.
pub struct AgentHarnessRunner<C: ControlPlane, G: InferenceGateway> {
    control_plane: C,
    inference_gateway: G,
    budget: HarnessBudget,
    stall_detector: StallDetector,
}

impl<C: ControlPlane, G: InferenceGateway> AgentHarnessRunner<C, G> {
    pub fn new(
        control_plane: C,
        inference_gateway: G,
        budget: HarnessBudget,
        stall_detector: StallDetector,
    ) -> Self {
        Self {
            control_plane,
            inference_gateway,
            budget,
            stall_detector,
        }
    }

    /// Runs the assignment to completion (success, failure, timeout, or stall) and reports the
    /// outcome to the control plane before returning it.
    ///
    /// v1 scope: a single inference turn, asking the model directly for the task's output, is
    /// treated as the whole run -- there is no multi-step action/tool loop yet, so the budget and
    /// stall-detector checks below are exercised for real (every turn is recorded and
    /// heartbeated) but will not usually trip on their own with only one turn taken. They stay in
    /// place as the bound a future multi-turn loop plugs into, rather than being removed and
    /// re-added later.
    pub async fn run(
        &mut self,
        assignment_id: AssignmentId,
    ) -> Result<HarnessOutcome, HarnessError> {
        let assignment = self.control_plane.fetch_assignment(assignment_id).await?;

        let outcome = if self.budget.is_exhausted() {
            HarnessOutcome::TimedOut
        } else if self.stall_detector.is_stalled() {
            HarnessOutcome::Stalled
        } else {
            self.take_turn(assignment_id, &assignment.objective).await
        };

        self.control_plane
            .submit_result(assignment_id, &outcome)
            .await?;
        Ok(outcome)
    }

    /// Takes the one inference turn v1 uses to produce a task's output. Never propagates an
    /// error out of `run` -- an inference failure becomes a reported [`HarnessOutcome::Failed`]
    /// instead, so a bad turn still ends in a `submit_result` call rather than an attempt stuck
    /// open forever. A heartbeat failure is logged and otherwise ignored: it's a liveness signal,
    /// not the outcome itself.
    async fn take_turn(&mut self, assignment_id: AssignmentId, prompt: &str) -> HarnessOutcome {
        let turn = match self
            .inference_gateway
            .complete_turn(assignment_id, prompt)
            .await
        {
            Ok(turn) => turn,
            Err(error) => {
                return HarnessOutcome::Failed {
                    reason: error.to_string(),
                }
            }
        };

        self.budget.record_turn(turn.tokens_spent);
        self.stall_detector.record(turn.content.clone());

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

        HarnessOutcome::Succeeded {
            output: turn.content,
        }
    }
}
