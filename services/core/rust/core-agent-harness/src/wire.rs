use serde::{Deserialize, Serialize};

/// Body of `POST /agent/assignments/{id}/heartbeat`.
#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct HeartbeatRequestBody {
    pub turns_used: u32,
    pub tokens_used: u64,
}

/// Body of `POST /agent/assignments/{id}/result`. `status` must match one of core-server's
/// `TaskAttemptStatus` enum constant names exactly (`SUCCEEDED`, `FAILED`, `TIMED_OUT`,
/// `STALLED`) -- Jackson deserializes an enum from its literal constant name.
#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ResultRequestBody {
    pub status: &'static str,
    pub output: Option<String>,
    pub failure_reason: Option<String>,
}

/// Mirrors core-server's `ConversationRole` enum -- which participant produced a message. Jackson
/// serializes a Java enum as its literal constant name, hence `UPPERCASE` rather than the usual
/// `camelCase`.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "UPPERCASE")]
pub enum ConversationRoleBody {
    System,
    User,
    Assistant,
    Tool,
}

/// Wire shape of one tool call: mirrors core-server's `InferenceToolCall` record.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ToolCallBody {
    pub id: String,
    pub name: String,
    pub arguments_json: String,
}

/// Wire shape of one conversation message: mirrors core-server's `ConversationMessage` record.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ConversationMessageBody {
    pub role: ConversationRoleBody,
    pub content: Option<String>,
    #[serde(default)]
    pub tool_calls: Vec<ToolCallBody>,
    pub tool_call_id: Option<String>,
    pub tool_name: Option<String>,
}

/// Body of `POST /agent/inference`. Unlike the assignment endpoints above, the assignment id
/// travels in the body here, not the path -- there is no per-assignment inference route.
#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct InferenceRequestBody {
    pub assignment_id: String,
    pub messages: Vec<ConversationMessageBody>,
}

/// Response of `POST /agent/inference`: core-server's `InferenceTurn` record.
#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct InferenceResponseBody {
    pub content: Option<String>,
    #[serde(default)]
    pub tool_calls: Vec<ToolCallBody>,
    pub tokens_spent: u64,
    #[serde(default)]
    pub finish_reason: Option<String>,
}

/// Body of `POST /agent/tool-calls`. Like `/agent/inference`, the assignment id travels in the
/// body -- there is no per-assignment route.
#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ToolCallInvocationRequestBody {
    pub assignment_id: String,
    pub tool_call_id: String,
    pub tool_name: String,
    pub arguments_json: String,
}

/// Response of `POST /agent/tool-calls`: core-server's `ToolCallInvocationResult` record.
#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ToolCallInvocationResultBody {
    pub tool_call_id: String,
    pub result_json: String,
}
