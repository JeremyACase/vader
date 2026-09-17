mod assignment;
mod budget;
mod control_plane;
mod core_server_adapter;
mod error;
mod inference_gateway;
mod runner;
mod stall_detector;
mod tool_executor;
mod wire;

use std::time::{Duration, Instant};

use assignment::{AssignmentId, TaskId};
use budget::HarnessBudget;
use control_plane::ControlPlane;
use core_server_adapter::CoreServerAdapter;
use error::HarnessError;
use runner::AgentHarnessRunner;
use stall_detector::StallDetector;

// The stall detector has no server-provided equivalent -- it is purely a local backstop, so its
// window stays a fixed local constant regardless of what a given assignment dispatches.
const DEFAULT_STALL_REPEATS: u32 = 3;

#[tokio::main(flavor = "current_thread")]
async fn main() -> Result<(), HarnessError> {
    let task_id = TaskId::parse(&required_env("TASK_ID")?)?;
    let assignment_id = AssignmentId::parse(&required_env("ASSIGNMENT_ID")?)?;
    let core_server_url = required_env("CORE_SERVER_URL")?;

    println!(
        "core-agent-harness starting: task={:?} assignment={:?} core_server={core_server_url}",
        task_id, assignment_id
    );

    let adapter = CoreServerAdapter::new(core_server_url);
    // Fetched once, up front, so the local HarnessBudget enforces the bounds core-server actually
    // dispatched for this assignment rather than a fixed local guess -- core-server enforces its
    // own copy of these same bounds server-side regardless (see AssignmentResponse); this is a
    // local backstop, not the source of truth.
    let assignment = adapter.fetch_assignment(assignment_id).await?;
    let budget = HarnessBudget::new(
        assignment.max_turns,
        assignment.max_tokens,
        Instant::now() + Duration::from_secs(assignment.deadline_seconds),
    );
    let stall_detector = StallDetector::new(DEFAULT_STALL_REPEATS);

    let mut runner = AgentHarnessRunner::new(
        adapter.clone(),
        adapter.clone(),
        adapter,
        budget,
        stall_detector,
    );
    let outcome = runner.run(assignment).await?;

    println!("core-agent-harness finished: {outcome:?}");
    Ok(())
}

fn required_env(key: &str) -> Result<String, HarnessError> {
    std::env::var(key).map_err(|_| HarnessError::MissingEnv(key.to_string()))
}
