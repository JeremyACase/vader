use crate::assignment::{Assignment, AssignmentId};
use crate::control_plane::ControlPlane;
use crate::error::HarnessError;
use crate::inference_gateway::{InferenceGateway, InferenceTurn};
use crate::runner::HarnessOutcome;

/// The harness's single external dependency: `core-server`. Adapts both the
/// [`ControlPlane`] and [`InferenceGateway`] traits onto `core-server`'s REST surface, since a
/// harness only ever needs to reach one host.
///
/// Every method here is a stub — `core-server` does not yet expose the `/agent/*` endpoints
/// this adapter targets. Fill each one in once that surface exists; the request/response shapes
/// below are the intended contract, not yet load-bearing.
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
}

impl ControlPlane for CoreServerAdapter {
    async fn fetch_assignment(
        &self,
        _assignment_id: AssignmentId,
    ) -> Result<Assignment, HarnessError> {
        // TODO(core-server): GET {base_url}/vader/core-server/agent/assignments/{assignment_id}
        let _ = &self.http_client;
        let _ = &self.base_url;
        todo!("fetch_assignment: core-server does not yet expose /agent/assignments/{{id}}")
    }

    async fn heartbeat(
        &self,
        _assignment_id: AssignmentId,
        _turns_used: u32,
        _tokens_used: u64,
    ) -> Result<(), HarnessError> {
        // TODO(core-server): POST {base_url}/vader/core-server/agent/assignments/{assignment_id}/heartbeat
        todo!("heartbeat: core-server does not yet expose /agent/assignments/{{id}}/heartbeat")
    }

    async fn submit_result(
        &self,
        _assignment_id: AssignmentId,
        _outcome: &HarnessOutcome,
    ) -> Result<(), HarnessError> {
        // TODO(core-server): POST {base_url}/vader/core-server/agent/assignments/{assignment_id}/result
        todo!("submit_result: core-server does not yet expose /agent/assignments/{{id}}/result")
    }
}

impl InferenceGateway for CoreServerAdapter {
    async fn complete_turn(
        &self,
        _assignment_id: AssignmentId,
        _prompt: &str,
    ) -> Result<InferenceTurn, HarnessError> {
        // TODO(core-server): POST {base_url}/vader/core-server/agent/inference
        todo!("complete_turn: core-server does not yet expose /agent/inference")
    }
}
