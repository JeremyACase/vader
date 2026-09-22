use crate::assignment::{Assignment, AssignmentId};
use crate::control_plane::ControlPlane;
use crate::error::HarnessError;
use crate::inference_gateway::{
    ConversationMessage, ConversationRole, InferenceGateway, InferenceTurn, ToolCall,
};
use crate::runner::HarnessOutcome;
use crate::tool_executor::{ToolExecutor, ToolResult};
use crate::wire::{
    ConversationMessageBody, ConversationRoleBody, HeartbeatRequestBody, InferenceRequestBody,
    InferenceResponseBody, ResultRequestBody, ToolCallBody, ToolCallInvocationRequestBody,
    ToolCallInvocationResultBody,
};

/// The harness's single external dependency: `core-server`. Adapts the [`ControlPlane`],
/// [`InferenceGateway`], and [`ToolExecutor`] traits onto `core-server`'s REST surface, since a
/// harness only ever needs to reach one host.
#[derive(Clone)]
pub struct CoreServerAdapter {
    base_url: String,
    http_client: reqwest::Client,
}

impl CoreServerAdapter {
    pub fn new(base_url: String) -> Self {
        Self {
            base_url,
            http_client: reqwest::Client::new(),
        }
    }

    async fn get<T: serde::de::DeserializeOwned>(&self, path: &str) -> Result<T, HarnessError> {
        let url = format!("{}{path}", self.base_url);
        log::debug!("GET {url}");
        let response = self.http_client.get(&url).send().await.map_err(|source| {
            log::warn!("GET {url} failed: {source}");
            HarnessError::ControlPlaneUnavailable(source.to_string())
        })?;
        Self::body_on_success(&url, response, HarnessError::ControlPlaneUnavailable).await
    }

    async fn post_no_content(
        &self,
        path: &str,
        body: &impl serde::Serialize,
    ) -> Result<(), HarnessError> {
        let url = format!("{}{path}", self.base_url);
        log::debug!("POST {url}");
        let response = self
            .http_client
            .post(&url)
            .json(body)
            .send()
            .await
            .map_err(|source| {
                log::warn!("POST {url} failed: {source}");
                HarnessError::ControlPlaneUnavailable(source.to_string())
            })?;
        Self::ensure_success(&url, response, HarnessError::ControlPlaneUnavailable)
            .await
            .map(|_| ())
    }

    async fn post_json<T: serde::de::DeserializeOwned>(
        &self,
        path: &str,
        body: &impl serde::Serialize,
        to_error: impl Fn(String) -> HarnessError,
    ) -> Result<T, HarnessError> {
        let url = format!("{}{path}", self.base_url);
        log::debug!("POST {url}");
        let response = self
            .http_client
            .post(&url)
            .json(body)
            .send()
            .await
            .map_err(|source| {
                log::warn!("POST {url} failed: {source}");
                to_error(source.to_string())
            })?;
        Self::body_on_success(&url, response, to_error).await
    }

    async fn ensure_success(
        url: &str,
        response: reqwest::Response,
        to_error: impl Fn(String) -> HarnessError,
    ) -> Result<reqwest::Response, HarnessError> {
        if response.status().is_success() {
            return Ok(response);
        }
        let status = response.status();
        let body = response.text().await.unwrap_or_default();
        log::warn!("{url} returned HTTP {status}: {body}");
        Err(to_error(format!("HTTP {status}: {body}")))
    }

    async fn body_on_success<T: serde::de::DeserializeOwned>(
        url: &str,
        response: reqwest::Response,
        to_error: impl Fn(String) -> HarnessError,
    ) -> Result<T, HarnessError> {
        let response = Self::ensure_success(url, response, &to_error).await?;
        response.json::<T>().await.map_err(|source| {
            log::warn!("{url} returned a body that could not be parsed: {source}");
            to_error(source.to_string())
        })
    }
}

impl ControlPlane for CoreServerAdapter {
    async fn fetch_assignment(
        &self,
        assignment_id: AssignmentId,
    ) -> Result<Assignment, HarnessError> {
        self.get(&format!(
            "/vader/core-server/agent/assignments/{}",
            assignment_id.0
        ))
        .await
    }

    async fn heartbeat(
        &self,
        assignment_id: AssignmentId,
        turns_used: u32,
        tokens_used: u64,
    ) -> Result<(), HarnessError> {
        let body = HeartbeatRequestBody {
            turns_used,
            tokens_used,
        };
        self.post_no_content(
            &format!(
                "/vader/core-server/agent/assignments/{}/heartbeat",
                assignment_id.0
            ),
            &body,
        )
        .await
    }

    async fn submit_result(
        &self,
        assignment_id: AssignmentId,
        outcome: &HarnessOutcome,
    ) -> Result<(), HarnessError> {
        let body = result_body_for(outcome);
        self.post_no_content(
            &format!(
                "/vader/core-server/agent/assignments/{}/result",
                assignment_id.0
            ),
            &body,
        )
        .await
    }
}

impl InferenceGateway for CoreServerAdapter {
    async fn complete_turn(
        &self,
        assignment_id: AssignmentId,
        messages: &[ConversationMessage],
    ) -> Result<InferenceTurn, HarnessError> {
        let body = InferenceRequestBody {
            assignment_id: assignment_id.0.to_string(),
            messages: messages.iter().map(to_message_body).collect(),
        };
        let parsed: InferenceResponseBody = self
            .post_json(
                "/vader/core-server/agent/inference",
                &body,
                HarnessError::InferenceUnavailable,
            )
            .await?;
        Ok(InferenceTurn {
            content: parsed.content,
            tool_calls: parsed
                .tool_calls
                .into_iter()
                .map(from_tool_call_body)
                .collect(),
            tokens_spent: parsed.tokens_spent,
        })
    }
}

impl ToolExecutor for CoreServerAdapter {
    async fn invoke_tool(
        &self,
        assignment_id: AssignmentId,
        tool_call: &ToolCall,
    ) -> Result<ToolResult, HarnessError> {
        let body = ToolCallInvocationRequestBody {
            assignment_id: assignment_id.0.to_string(),
            tool_call_id: tool_call.id.clone(),
            tool_name: tool_call.name.clone(),
            arguments_json: tool_call.arguments_json.clone(),
        };
        let parsed: ToolCallInvocationResultBody = self
            .post_json(
                "/vader/core-server/agent/tool-calls",
                &body,
                HarnessError::ToolInvocationUnavailable,
            )
            .await?;
        Ok(ToolResult {
            tool_call_id: parsed.tool_call_id,
            result_json: parsed.result_json,
        })
    }
}

fn to_message_body(message: &ConversationMessage) -> ConversationMessageBody {
    ConversationMessageBody {
        role: to_role_body(message.role),
        content: message.content.clone(),
        tool_calls: message.tool_calls.iter().map(to_tool_call_body).collect(),
        tool_call_id: message.tool_call_id.clone(),
        tool_name: message.tool_name.clone(),
    }
}

fn to_role_body(role: ConversationRole) -> ConversationRoleBody {
    match role {
        ConversationRole::System => ConversationRoleBody::System,
        ConversationRole::User => ConversationRoleBody::User,
        ConversationRole::Assistant => ConversationRoleBody::Assistant,
        ConversationRole::Tool => ConversationRoleBody::Tool,
    }
}

fn to_tool_call_body(tool_call: &ToolCall) -> ToolCallBody {
    ToolCallBody {
        id: tool_call.id.clone(),
        name: tool_call.name.clone(),
        arguments_json: tool_call.arguments_json.clone(),
    }
}

fn from_tool_call_body(body: ToolCallBody) -> ToolCall {
    ToolCall {
        id: body.id,
        name: body.name,
        arguments_json: body.arguments_json,
    }
}

fn result_body_for(outcome: &HarnessOutcome) -> ResultRequestBody {
    match outcome {
        HarnessOutcome::Succeeded { output } => ResultRequestBody {
            status: "SUCCEEDED",
            output: Some(output.clone()),
            failure_reason: None,
        },
        HarnessOutcome::Failed { reason } => ResultRequestBody {
            status: "FAILED",
            output: None,
            failure_reason: Some(reason.clone()),
        },
        HarnessOutcome::TimedOut => ResultRequestBody {
            status: "TIMED_OUT",
            output: None,
            failure_reason: Some("The harness's deadline elapsed before it finished.".to_string()),
        },
        HarnessOutcome::Stalled => ResultRequestBody {
            status: "STALLED",
            output: None,
            failure_reason: Some(
                "The harness repeated the same action with no progress.".to_string(),
            ),
        },
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn result_body_for_succeeded_carries_output_and_no_failure_reason() {
        let body = result_body_for(&HarnessOutcome::Succeeded {
            output: "done".to_string(),
        });
        assert_eq!(body.status, "SUCCEEDED");
        assert_eq!(body.output.as_deref(), Some("done"));
        assert!(body.failure_reason.is_none());
    }

    #[test]
    fn result_body_for_failed_carries_reason_and_no_output() {
        let body = result_body_for(&HarnessOutcome::Failed {
            reason: "boom".to_string(),
        });
        assert_eq!(body.status, "FAILED");
        assert!(body.output.is_none());
        assert_eq!(body.failure_reason.as_deref(), Some("boom"));
    }

    #[test]
    fn result_body_for_timed_out_and_stalled_use_fixed_status_strings() {
        assert_eq!(
            result_body_for(&HarnessOutcome::TimedOut).status,
            "TIMED_OUT"
        );
        assert_eq!(result_body_for(&HarnessOutcome::Stalled).status, "STALLED");
    }

    #[test]
    fn to_message_body_roundtrips_a_tool_result_message() {
        let message = ConversationMessage::tool_result("call-1", "get_object_content", "{}");

        let body = to_message_body(&message);

        assert_eq!(body.role, ConversationRoleBody::Tool);
        assert_eq!(body.tool_call_id.as_deref(), Some("call-1"));
        assert_eq!(body.tool_name.as_deref(), Some("get_object_content"));
        assert_eq!(body.content.as_deref(), Some("{}"));
    }

    #[test]
    fn to_message_body_carries_assistant_tool_calls() {
        let tool_call = ToolCall {
            id: "call-1".to_string(),
            name: "get_object_content".to_string(),
            arguments_json: "{\"id\":\"abc\"}".to_string(),
        };
        let message = ConversationMessage::assistant_tool_calls(vec![tool_call]);

        let body = to_message_body(&message);

        assert_eq!(body.role, ConversationRoleBody::Assistant);
        assert_eq!(body.tool_calls.len(), 1);
        assert_eq!(body.tool_calls[0].name, "get_object_content");
    }
}
