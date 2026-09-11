use crate::assignment::{Assignment, AssignmentId};
use crate::control_plane::ControlPlane;
use crate::error::HarnessError;
use crate::inference_gateway::{InferenceGateway, InferenceTurn};
use crate::runner::HarnessOutcome;
use crate::wire::{
    HeartbeatRequestBody, InferenceRequestBody, InferenceResponseBody, ResultRequestBody,
};

/// The harness's single external dependency: `core-server`. Adapts both the
/// [`ControlPlane`] and [`InferenceGateway`] traits onto `core-server`'s REST surface, since a
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
        let response = self
            .http_client
            .get(&url)
            .send()
            .await
            .map_err(|source| HarnessError::ControlPlaneUnavailable(source.to_string()))?;
        Self::body_on_success(response, HarnessError::ControlPlaneUnavailable).await
    }

    async fn post_no_content(
        &self,
        path: &str,
        body: &impl serde::Serialize,
    ) -> Result<(), HarnessError> {
        let url = format!("{}{path}", self.base_url);
        let response = self
            .http_client
            .post(&url)
            .json(body)
            .send()
            .await
            .map_err(|source| HarnessError::ControlPlaneUnavailable(source.to_string()))?;
        Self::ensure_success(response, HarnessError::ControlPlaneUnavailable)
            .await
            .map(|_| ())
    }

    async fn ensure_success(
        response: reqwest::Response,
        to_error: impl Fn(String) -> HarnessError,
    ) -> Result<reqwest::Response, HarnessError> {
        if response.status().is_success() {
            return Ok(response);
        }
        let status = response.status();
        let body = response.text().await.unwrap_or_default();
        Err(to_error(format!("HTTP {status}: {body}")))
    }

    async fn body_on_success<T: serde::de::DeserializeOwned>(
        response: reqwest::Response,
        to_error: impl Fn(String) -> HarnessError,
    ) -> Result<T, HarnessError> {
        let response = Self::ensure_success(response, &to_error).await?;
        response
            .json::<T>()
            .await
            .map_err(|source| to_error(source.to_string()))
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
        prompt: &str,
    ) -> Result<InferenceTurn, HarnessError> {
        let url = format!("{}/vader/core-server/agent/inference", self.base_url);
        let body = InferenceRequestBody {
            assignment_id: assignment_id.0.to_string(),
            prompt: prompt.to_string(),
        };
        let response = self
            .http_client
            .post(&url)
            .json(&body)
            .send()
            .await
            .map_err(|source| HarnessError::InferenceUnavailable(source.to_string()))?;
        let parsed: InferenceResponseBody =
            Self::body_on_success(response, HarnessError::InferenceUnavailable).await?;
        Ok(InferenceTurn {
            content: parsed.content,
            tokens_spent: parsed.tokens_spent,
        })
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
}
