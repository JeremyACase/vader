use crate::assignment::{Assignment, AssignmentId};
use crate::error::HarnessError;
use crate::runner::HarnessOutcome;

/// The harness's control-plane contract with `core-server`: fetch the work order, report
/// liveness, and hand back the terminal outcome. Implemented by [`crate::core_server_adapter::CoreServerAdapter`];
/// a test double implements it too, so [`crate::runner::AgentHarnessRunner`] never needs a real
/// network call to be unit-tested.
pub trait ControlPlane {
    /// Fetches the work order for `assignment_id`.
    async fn fetch_assignment(
        &self,
        assignment_id: AssignmentId,
    ) -> Result<Assignment, HarnessError>;

    /// Reports liveness and progress partway through a run.
    async fn heartbeat(
        &self,
        assignment_id: AssignmentId,
        turns_used: u32,
        tokens_used: u64,
    ) -> Result<(), HarnessError>;

    /// Reports the run's terminal outcome. Always the last control-plane call a harness makes.
    async fn submit_result(
        &self,
        assignment_id: AssignmentId,
        outcome: &HarnessOutcome,
    ) -> Result<(), HarnessError>;
}
