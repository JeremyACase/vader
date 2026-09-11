use serde::{Deserialize, Serialize};
use uuid::Uuid;

use crate::error::HarnessError;

/// Identifies which node of a `TaskGraph` this harness is responsible for. Stable across
/// retries: a task may be attempted more than once, but its `TaskId` never changes.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct TaskId(pub Uuid);

/// Identifies exactly one dispatch of a [`TaskId`] to a harness. `core-server` mints a fresh
/// `AssignmentId` per attempt, so a heartbeat or result carrying a stale id can be rejected
/// instead of corrupting a newer attempt's state.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct AssignmentId(pub Uuid);

impl TaskId {
    /// Parses a `TaskId` from its `TASK_ID` environment/string representation.
    pub fn parse(raw: &str) -> Result<Self, HarnessError> {
        let id = Uuid::parse_str(raw).map_err(|source| HarnessError::InvalidId {
            field: "TASK_ID",
            source,
        })?;
        Ok(Self(id))
    }
}

impl AssignmentId {
    /// Parses an `AssignmentId` from its `ASSIGNMENT_ID` environment/string representation.
    pub fn parse(raw: &str) -> Result<Self, HarnessError> {
        let id = Uuid::parse_str(raw).map_err(|source| HarnessError::InvalidId {
            field: "ASSIGNMENT_ID",
            source,
        })?;
        Ok(Self(id))
    }
}

/// The work order `core-server` hands back for a given [`AssignmentId`]: what to do and the
/// bounds to do it within. Fetched once, at harness startup, from the control plane.
///
/// TODO(core-server): this shape is provisional until the `/agent/assignments/{id}` endpoint
/// exists. Expect it to grow dependency-task outputs and file pointers once tasks can consume
/// sibling results.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Assignment {
    pub task_id: TaskId,
    pub assignment_id: AssignmentId,
    pub objective: String,
    pub max_turns: u32,
    pub max_tokens: u64,
    pub deadline_seconds: u64,
}
