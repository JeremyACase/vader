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
/// Generic over both collaborators (rather than `dyn ControlPlane` / `dyn InferenceGateway`) —
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
    pub async fn run(
        &mut self,
        assignment_id: AssignmentId,
    ) -> Result<HarnessOutcome, HarnessError> {
        let _ = assignment_id;
        let _ = &self.control_plane;
        let _ = &self.inference_gateway;
        let _ = &self.budget;
        let _ = &self.stall_detector;
        todo!(
            "turn loop: fetch_assignment, then repeatedly call complete_turn, record_turn, \
             heartbeat, and check is_exhausted()/is_stalled() until a terminal outcome, then \
             submit_result"
        )
    }
}
