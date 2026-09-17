use crate::assignment::AssignmentId;
use crate::error::HarnessError;
use crate::inference_gateway::ToolCall;

/// One tool's raw result, ready to fold back into the conversation as a `Tool`
/// [`crate::inference_gateway::ConversationMessage`].
#[derive(Debug, Clone)]
pub struct ToolResult {
    pub tool_call_id: String,
    pub result_json: String,
}

/// The harness's path to actually invoking a tool call a model requested during an inference
/// turn. Every call goes through the same tool-callback lookup an MCP client would (via
/// `core-server`'s `McpToolCallbackRegistry`), so invocation is logged identically regardless of
/// caller. Implemented by [`crate::core_server_adapter::CoreServerAdapter`].
pub trait ToolExecutor {
    /// Executes one tool call on behalf of the given assignment.
    async fn invoke_tool(
        &self,
        assignment_id: AssignmentId,
        tool_call: &ToolCall,
    ) -> Result<ToolResult, HarnessError>;
}
