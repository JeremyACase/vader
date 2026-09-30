//! The agent harness: executes exactly one task-graph subtask, with `core-server` as its only
//! control plane, inference gateway and tool executor. The modules stay private; the binary only
//! needs [`run_from_env`].

mod assignment;
mod budget;
mod control_plane;
mod conversation;
mod core_server_adapter;
mod error;
mod harness_outcome;
mod inference_gateway;
mod prompts;
mod runner;
mod stall_detector;
mod tool_executor;
mod unfinished_answer_guard;
mod wire;

use std::time::{Duration, Instant};

use assignment::{Assignment, AssignmentId, TaskId};
use budget::HarnessBudget;
use control_plane::ControlPlane;
use core_server_adapter::CoreServerAdapter;
use runner::AgentHarnessRunner;
use stall_detector::StallDetector;

pub use error::HarnessError;

// The stall detector has no server-provided equivalent -- it is purely a local backstop, so its
// window stays a fixed local constant regardless of what a given assignment dispatches.
const DEFAULT_STALL_REPEATS: u32 = 3;

/// Runs the assignment named by the `TASK_ID`, `ASSIGNMENT_ID` and `CORE_SERVER_URL` environment
/// variables, reporting its outcome to core-server.
pub async fn run_from_env() -> Result<(), HarnessError> {
    let task_id = TaskId::parse(&required_env("TASK_ID")?)?;
    let assignment_id = AssignmentId::parse(&required_env("ASSIGNMENT_ID")?)?;
    let core_server_url = required_env("CORE_SERVER_URL")?;
    log::info!(
        "core-agent-harness starting: task={task_id:?} assignment={assignment_id:?} \
         core_server={core_server_url}"
    );
    run_assignment(CoreServerAdapter::new(core_server_url), assignment_id).await
}

/// Fetches the work order and runs it to a reported outcome.
async fn run_assignment(
    adapter: CoreServerAdapter,
    assignment_id: AssignmentId,
) -> Result<(), HarnessError> {
    let assignment = adapter.fetch_assignment(assignment_id).await?;
    let mut runner = AgentHarnessRunner::new(
        adapter.clone(),
        adapter.clone(),
        adapter,
        budget_for(&assignment),
        StallDetector::new(DEFAULT_STALL_REPEATS),
    );
    let outcome = runner.run(assignment).await?;
    log::info!("core-agent-harness finished: {outcome:?}");
    Ok(())
}

/// Sizes the local budget from the bounds core-server actually dispatched for this assignment
/// rather than a fixed local guess. core-server enforces its own copy of these bounds regardless;
/// this is a local backstop, not the source of truth.
fn budget_for(assignment: &Assignment) -> HarnessBudget {
    HarnessBudget::new(
        assignment.max_turns,
        assignment.max_tokens,
        Instant::now() + Duration::from_secs(assignment.deadline_seconds),
    )
}

fn required_env(key: &str) -> Result<String, HarnessError> {
    std::env::var(key).map_err(|_| {
        log::error!("required environment variable {key} is not set");
        HarnessError::MissingEnv(key.to_string())
    })
}
