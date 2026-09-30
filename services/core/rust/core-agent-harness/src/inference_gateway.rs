use crate::assignment::AssignmentId;
use crate::conversation::{ConversationMessage, InferenceTurn};
use crate::error::HarnessError;

/// The **only** path a harness has to any LLM, internal or external to the cluster.
///
/// A harness must never hold a provider API key or call a model endpoint directly — every
/// inference call is proxied through `core-server`, which owns provider config, attributes
/// spend per assignment, logs the full transcript, and enforces budgets server-side as a
/// backstop independent of this process. Implemented by
/// [`crate::core_server_adapter::CoreServerAdapter`].
pub trait InferenceGateway {
    /// Requests one model turn for the given assignment and running conversation.
    async fn complete_turn(
        &self,
        assignment_id: AssignmentId,
        messages: &[ConversationMessage],
    ) -> Result<InferenceTurn, HarnessError>;
}
