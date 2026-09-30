use serde::{Deserialize, Serialize};
use uuid::Uuid;

use crate::error::HarnessError;

/// Identifies which node of a `TaskGraph` this harness is responsible for. Stable across
/// retries: a task may be attempted more than once, but its `TaskId` never changes.
///
/// The wrapped `Uuid` is private so an id only ever comes from parsing or deserializing what
/// core-server sent, never from an arbitrary `Uuid` (such as an assignment's) rewrapped.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct TaskId(Uuid);

/// Identifies exactly one dispatch of a [`TaskId`] to a harness. `core-server` mints a fresh
/// `AssignmentId` per attempt, so a heartbeat or result carrying a stale id can be rejected
/// instead of corrupting a newer attempt's state. Private for the same reason as [`TaskId`].
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct AssignmentId(Uuid);

impl TaskId {
    /// Parses a `TaskId` from its `TASK_ID` environment/string representation.
    pub fn parse(raw: &str) -> Result<Self, HarnessError> {
        parse_uuid(raw, "TASK_ID").map(Self)
    }
}

impl AssignmentId {
    /// Parses an `AssignmentId` from its `ASSIGNMENT_ID` environment/string representation.
    pub fn parse(raw: &str) -> Result<Self, HarnessError> {
        parse_uuid(raw, "ASSIGNMENT_ID").map(Self)
    }

    pub fn as_uuid(&self) -> Uuid {
        self.0
    }
}

fn parse_uuid(raw: &str, field: &'static str) -> Result<Uuid, HarnessError> {
    Uuid::parse_str(raw).map_err(|source| HarnessError::InvalidId { field, source })
}

/// The work order `core-server` hands back for a given [`AssignmentId`]: what to do and the
/// bounds to do it within. Fetched once, at harness startup, from the control plane.
///
/// Field names are camelCase on the wire (matching every other JSON API `core-server` exposes,
/// all plain Jackson-default records) even though this struct's own fields are idiomatic
/// snake_case Rust.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Assignment {
    pub task_id: TaskId,
    pub assignment_id: AssignmentId,
    pub objective: String,
    /// Background a task's own short `objective` never carries on its own: the original
    /// client-submitted request, any files attached to it, and the results of any prerequisite
    /// tasks this one depends on. Always at least the original request.
    pub context: String,
    pub max_turns: u32,
    pub max_tokens: u64,
    pub deadline_seconds: u64,
}

#[cfg(test)]
mod tests {
    use super::*;

    const RAW_ID: &str = "00000000-0000-0000-0000-000000000001";

    #[test]
    fn assignment_id_parses_to_the_uuid_it_was_given() {
        let assignment_id = AssignmentId::parse(RAW_ID).unwrap();
        assert_eq!(assignment_id.as_uuid(), Uuid::from_u128(1));
    }

    #[test]
    fn an_invalid_id_names_the_field_it_came_from() {
        let error = TaskId::parse("not-a-uuid").unwrap_err();
        assert!(matches!(
            error,
            HarnessError::InvalidId {
                field: "TASK_ID",
                ..
            }
        ));
    }
}
