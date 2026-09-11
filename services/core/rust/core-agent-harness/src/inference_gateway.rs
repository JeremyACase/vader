use crate::assignment::AssignmentId;
use crate::error::HarnessError;

/// One model turn's result: the text produced and how many tokens it cost against the
/// assignment's budget.
#[derive(Debug, Clone)]
pub struct InferenceTurn {
    pub content: String,
    pub tokens_spent: u64,
}

/// The **only** path a harness has to any LLM, internal or external to the cluster.
///
/// A harness must never hold a provider API key or call a model endpoint directly — every
/// inference call is proxied through `core-server`, which owns provider config, attributes
/// spend per assignment, logs the full transcript, and enforces budgets server-side as a
/// backstop independent of this process. Implemented by
/// [`crate::core_server_adapter::CoreServerAdapter`].
pub trait InferenceGateway {
    /// Requests one model turn for the given assignment.
    async fn complete_turn(
        &self,
        assignment_id: AssignmentId,
        prompt: &str,
    ) -> Result<InferenceTurn, HarnessError>;
}
