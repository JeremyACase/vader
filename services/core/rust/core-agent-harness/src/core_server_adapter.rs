use crate::assignment::{Assignment, AssignmentId};
use crate::control_plane::ControlPlane;
use crate::conversation::{ConversationMessage, InferenceTurn, ToolCall};
use crate::error::HarnessError;
use crate::harness_outcome::HarnessOutcome;
use crate::inference_gateway::InferenceGateway;
use crate::tool_executor::{ToolExecutor, ToolResult};
use crate::wire::{
    ConversationMessageBody, HeartbeatRequestBody, InferenceRequestBody, InferenceResponseBody,
    ResultRequestBody, ToolCallInvocationRequestBody, ToolCallInvocationResultBody,
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
        let status = response.status();
        if status.is_success() {
            Ok(response)
        } else {
            let body = response.text().await.unwrap_or_default();
            log::warn!("{url} returned HTTP {status}: {body}");
            Err(to_error(format!("HTTP {status}: {body}")))
        }
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
            "/vader/core-server/agent/assignments/{assignment_id}"
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
            &format!("/vader/core-server/agent/assignments/{assignment_id}/heartbeat"),
            &body,
        )
        .await
    }

    async fn submit_result(
        &self,
        assignment_id: AssignmentId,
        outcome: &HarnessOutcome,
    ) -> Result<(), HarnessError> {
        self.post_no_content(
            &format!("/vader/core-server/agent/assignments/{assignment_id}/result"),
            &ResultRequestBody::from(outcome),
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
            assignment_id: assignment_id.to_string(),
            messages: messages.iter().map(ConversationMessageBody::from).collect(),
        };
        let parsed: InferenceResponseBody = self
            .post_json(
                "/vader/core-server/agent/inference",
                &body,
                HarnessError::InferenceUnavailable,
            )
            .await?;
        Ok(parsed.into())
    }
}

impl ToolExecutor for CoreServerAdapter {
    async fn invoke_tool(
        &self,
        assignment_id: AssignmentId,
        tool_call: &ToolCall,
    ) -> Result<ToolResult, HarnessError> {
        let body = ToolCallInvocationRequestBody {
            assignment_id: assignment_id.to_string(),
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
        Ok(parsed.into())
    }
}
