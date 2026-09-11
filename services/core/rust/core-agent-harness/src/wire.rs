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

/// Body of `POST /agent/inference`. Unlike the assignment endpoints above, the assignment id
/// travels in the body here, not the path -- there is no per-assignment inference route.
#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct InferenceRequestBody {
    pub assignment_id: String,
    pub prompt: String,
}

/// Response of `POST /agent/inference`: core-server's `InferenceTurn` record.
#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct InferenceResponseBody {
    pub content: String,
    pub tokens_spent: u64,
}
