use crate::assignment::AssignmentId;
use crate::error::HarnessError;

/// Which participant produced a [`ConversationMessage`], mirroring core-server's
/// `ConversationRole`.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ConversationRole {
    System,
    User,
    Assistant,
    Tool,
}

/// One tool call the model requested (on an `Assistant` message) or the harness is reporting the
/// result of (on a `Tool` message).
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ToolCall {
    pub id: String,
    pub name: String,
    pub arguments_json: String,
}

/// One message in the running conversation sent to `core-server` on every inference turn. Which
/// fields are populated depends on `role` -- see core-server's `ConversationMessage` Javadoc for
/// the full mapping.
#[derive(Debug, Clone)]
pub struct ConversationMessage {
    pub role: ConversationRole,
    pub content: Option<String>,
    pub tool_calls: Vec<ToolCall>,
    pub tool_call_id: Option<String>,
    pub tool_name: Option<String>,
}

impl ConversationMessage {
    /// A `System` message: instructions for the model, with no tool calls.
    pub fn system(content: impl Into<String>) -> Self {
        Self {
            role: ConversationRole::System,
            content: Some(content.into()),
            tool_calls: Vec::new(),
            tool_call_id: None,
            tool_name: None,
        }
    }

    /// A `User` message: the objective or a follow-up, with no tool calls.
    pub fn user(content: impl Into<String>) -> Self {
        Self {
            role: ConversationRole::User,
            content: Some(content.into()),
            tool_calls: Vec::new(),
            tool_call_id: None,
            tool_name: None,
        }
    }

    /// An `Assistant` message replaying the tool calls a prior turn requested, so the following
    /// `Tool` messages have something to correlate against.
    pub fn assistant_tool_calls(tool_calls: Vec<ToolCall>) -> Self {
        Self {
            role: ConversationRole::Assistant,
            content: None,
            tool_calls,
            tool_call_id: None,
            tool_name: None,
        }
    }

    /// A `Tool` message: one tool call's result, folded back into the conversation.
    pub fn tool_result(
        tool_call_id: impl Into<String>,
        tool_name: impl Into<String>,
        result: impl Into<String>,
    ) -> Self {
        Self {
            role: ConversationRole::Tool,
            content: Some(result.into()),
            tool_calls: Vec::new(),
            tool_call_id: Some(tool_call_id.into()),
            tool_name: Some(tool_name.into()),
        }
    }
}

/// One model turn's result: either a final answer (`tool_calls` empty) or a request to call one
/// or more tools before the model can continue (`tool_calls` populated, `content` possibly
/// absent), plus how many tokens the turn cost against the assignment's budget.
#[derive(Debug, Clone)]
pub struct InferenceTurn {
    pub content: Option<String>,
    pub tool_calls: Vec<ToolCall>,
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
    /// Requests one model turn for the given assignment and running conversation.
    async fn complete_turn(
        &self,
        assignment_id: AssignmentId,
        messages: &[ConversationMessage],
    ) -> Result<InferenceTurn, HarnessError>;
}
