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

const ASSIGNMENTS_PATH: &str = "/vader/core-server/agent/assignments";
const INFERENCE_PATH: &str = "/vader/core-server/agent/inference";
const TOOL_CALLS_PATH: &str = "/vader/core-server/agent/tool-calls";

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
        let response = self.http_client.get(&url).send().await;
        let response = Self::sent(&url, response, HarnessError::ControlPlaneUnavailable)?;
        Self::body_on_success(&url, response, HarnessError::ControlPlaneUnavailable).await
    }

    async fn post_no_content(
        &self,
        path: &str,
        body: &impl serde::Serialize,
    ) -> Result<(), HarnessError> {
        let url = format!("{}{path}", self.base_url);
        log::debug!("POST {url}");
        let response = self.http_client.post(&url).json(body).send().await;
        let response = Self::sent(&url, response, HarnessError::ControlPlaneUnavailable)?;
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
        let response = self.http_client.post(&url).json(body).send().await;
        let response = Self::sent(&url, response, &to_error)?;
        Self::body_on_success(&url, response, to_error).await
    }

    /// Turns a request that never got a response (connection refused, timeout) into the caller's
    /// error variant.
    fn sent(
        url: &str,
        response: reqwest::Result<reqwest::Response>,
        to_error: impl Fn(String) -> HarnessError,
    ) -> Result<reqwest::Response, HarnessError> {
        response.map_err(|source| {
            log::warn!("{url} failed: {source}");
            to_error(source.to_string())
        })
    }

    async fn ensure_success(
        url: &str,
        response: reqwest::Response,
        to_error: impl Fn(String) -> HarnessError,
    ) -> Result<reqwest::Response, HarnessError> {
        if response.status().is_success() {
            Ok(response)
        } else {
            Err(Self::http_error(url, response, to_error).await)
        }
    }

    /// Builds the error for a non-2xx response, carrying its status and body so core-server
    /// answering with an error reads differently from core-server being unreachable.
    async fn http_error(
        url: &str,
        response: reqwest::Response,
        to_error: impl Fn(String) -> HarnessError,
    ) -> HarnessError {
        let status = response.status();
        let body = response.text().await.unwrap_or_default();
        log::warn!("{url} returned HTTP {status}: {body}");
        to_error(format!("HTTP {status}: {body}"))
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

fn assignment_path(assignment_id: AssignmentId, suffix: &str) -> String {
    format!("{ASSIGNMENTS_PATH}/{}{suffix}", assignment_id.as_uuid())
}

impl ControlPlane for CoreServerAdapter {
    async fn fetch_assignment(
        &self,
        assignment_id: AssignmentId,
    ) -> Result<Assignment, HarnessError> {
        self.get(&assignment_path(assignment_id, "")).await
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
        self.post_no_content(&assignment_path(assignment_id, "/heartbeat"), &body)
            .await
    }

    async fn submit_result(
        &self,
        assignment_id: AssignmentId,
        outcome: &HarnessOutcome,
    ) -> Result<(), HarnessError> {
        let body = ResultRequestBody::from(outcome);
        self.post_no_content(&assignment_path(assignment_id, "/result"), &body)
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
            assignment_id: assignment_id.as_uuid().to_string(),
            messages: messages.iter().map(ConversationMessageBody::from).collect(),
        };
        self.post_json::<InferenceResponseBody>(
            INFERENCE_PATH,
            &body,
            HarnessError::InferenceUnavailable,
        )
        .await
        .map(InferenceTurn::from)
    }
}

impl ToolExecutor for CoreServerAdapter {
    async fn invoke_tool(
        &self,
        assignment_id: AssignmentId,
        tool_call: &ToolCall,
    ) -> Result<ToolResult, HarnessError> {
        let body = ToolCallInvocationRequestBody {
            assignment_id: assignment_id.as_uuid().to_string(),
            tool_call_id: tool_call.id.clone(),
            tool_name: tool_call.name.clone(),
            arguments_json: tool_call.arguments_json.clone(),
        };
        self.post_json::<ToolCallInvocationResultBody>(
            TOOL_CALLS_PATH,
            &body,
            HarnessError::ToolInvocationUnavailable,
        )
        .await
        .map(ToolResult::from)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn assignment_path_puts_the_id_between_the_base_and_the_suffix() {
        let assignment_id = AssignmentId::parse("00000000-0000-0000-0000-000000000001").unwrap();
        assert_eq!(
            assignment_path(assignment_id, "/heartbeat"),
            "/vader/core-server/agent/assignments/00000000-0000-0000-0000-000000000001/heartbeat"
        );
    }
}
