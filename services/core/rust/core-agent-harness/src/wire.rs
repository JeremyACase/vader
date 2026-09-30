use serde::{Deserialize, Serialize};

use crate::conversation::{ConversationMessage, ConversationRole, InferenceTurn, ToolCall};
use crate::harness_outcome::HarnessOutcome;
use crate::tool_executor::ToolResult;

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

impl From<ConversationRole> for ConversationRoleBody {
    fn from(role: ConversationRole) -> Self {
        match role {
            ConversationRole::System => Self::System,
            ConversationRole::User => Self::User,
            ConversationRole::Assistant => Self::Assistant,
            ConversationRole::Tool => Self::Tool,
        }
    }
}

impl From<&ToolCall> for ToolCallBody {
    fn from(tool_call: &ToolCall) -> Self {
        Self {
            id: tool_call.id.clone(),
            name: tool_call.name.clone(),
            arguments_json: tool_call.arguments_json.clone(),
        }
    }
}

impl From<ToolCallBody> for ToolCall {
    fn from(body: ToolCallBody) -> Self {
        Self {
            id: body.id,
            name: body.name,
            arguments_json: body.arguments_json,
        }
    }
}

impl From<&ConversationMessage> for ConversationMessageBody {
    fn from(message: &ConversationMessage) -> Self {
        Self {
            role: message.role.into(),
            content: message.content.clone(),
            tool_calls: message.tool_calls.iter().map(ToolCallBody::from).collect(),
            tool_call_id: message.tool_call_id.clone(),
            tool_name: message.tool_name.clone(),
        }
    }
}

impl From<InferenceResponseBody> for InferenceTurn {
    fn from(body: InferenceResponseBody) -> Self {
        Self {
            content: body.content,
            tool_calls: body.tool_calls.into_iter().map(ToolCall::from).collect(),
            tokens_spent: body.tokens_spent,
            finish_reason: body.finish_reason,
        }
    }
}

impl From<ToolCallInvocationResultBody> for ToolResult {
    fn from(body: ToolCallInvocationResultBody) -> Self {
        Self {
            tool_call_id: body.tool_call_id,
            result_json: body.result_json,
        }
    }
}

impl From<&HarnessOutcome> for ResultRequestBody {
    fn from(outcome: &HarnessOutcome) -> Self {
        match outcome {
            HarnessOutcome::Succeeded { output } => Self {
                status: "SUCCEEDED",
                output: Some(output.clone()),
                failure_reason: None,
            },
            HarnessOutcome::Failed { reason } => Self {
                status: "FAILED",
                output: None,
                failure_reason: Some(reason.clone()),
            },
            HarnessOutcome::TimedOut => Self {
                status: "TIMED_OUT",
                output: None,
                failure_reason: Some(
                    "The harness's deadline elapsed before it finished.".to_string(),
                ),
            },
            HarnessOutcome::Stalled => Self {
                status: "STALLED",
                output: None,
                failure_reason: Some(
                    "The harness repeated the same action with no progress.".to_string(),
                ),
            },
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn succeeded_result_body_carries_output_and_no_failure_reason() {
        let body = ResultRequestBody::from(&HarnessOutcome::Succeeded {
            output: "done".to_string(),
        });
        assert_eq!(body.status, "SUCCEEDED");
        assert_eq!(body.output.as_deref(), Some("done"));
        assert!(body.failure_reason.is_none());
    }

    #[test]
    fn failed_result_body_carries_reason_and_no_output() {
        let body = ResultRequestBody::from(&HarnessOutcome::Failed {
            reason: "boom".to_string(),
        });
        assert_eq!(body.status, "FAILED");
        assert!(body.output.is_none());
        assert_eq!(body.failure_reason.as_deref(), Some("boom"));
    }

    #[test]
    fn timed_out_and_stalled_result_bodies_use_fixed_status_strings() {
        assert_eq!(
            ResultRequestBody::from(&HarnessOutcome::TimedOut).status,
            "TIMED_OUT"
        );
        assert_eq!(
            ResultRequestBody::from(&HarnessOutcome::Stalled).status,
            "STALLED"
        );
    }

    #[test]
    fn message_body_carries_a_tool_result_message() {
        let message = ConversationMessage::tool_result("call-1", "get_object_content", "{}");

        let body = ConversationMessageBody::from(&message);

        assert_eq!(body.role, ConversationRoleBody::Tool);
        assert_eq!(body.tool_call_id.as_deref(), Some("call-1"));
        assert_eq!(body.tool_name.as_deref(), Some("get_object_content"));
        assert_eq!(body.content.as_deref(), Some("{}"));
    }

    #[test]
    fn message_body_carries_assistant_tool_calls() {
        let tool_call = ToolCall {
            id: "call-1".to_string(),
            name: "get_object_content".to_string(),
            arguments_json: "{\"id\":\"abc\"}".to_string(),
        };
        let message = ConversationMessage::assistant_tool_calls(vec![tool_call]);

        let body = ConversationMessageBody::from(&message);

        assert_eq!(body.role, ConversationRoleBody::Assistant);
        assert_eq!(body.tool_calls.len(), 1);
        assert_eq!(body.tool_calls[0].name, "get_object_content");
    }

    #[test]
    fn inference_turn_takes_the_response_tool_calls() {
        let body = InferenceResponseBody {
            content: None,
            tool_calls: vec![ToolCallBody {
                id: "call-1".to_string(),
                name: "run_python_code".to_string(),
                arguments_json: "{}".to_string(),
            }],
            tokens_spent: 7,
            finish_reason: Some("stop".to_string()),
        };

        let turn = InferenceTurn::from(body);

        assert_eq!(turn.tool_calls[0].name, "run_python_code");
        assert_eq!(turn.tokens_spent, 7);
    }
}
