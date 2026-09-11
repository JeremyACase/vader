mod assignment;
mod budget;
mod control_plane;
mod core_server_adapter;
mod error;
mod inference_gateway;
mod runner;
mod stall_detector;
mod wire;

use std::time::{Duration, Instant};

use assignment::{AssignmentId, TaskId};
use budget::HarnessBudget;
use core_server_adapter::CoreServerAdapter;
use error::HarnessError;
use runner::AgentHarnessRunner;
use stall_detector::StallDetector;

// Local bounds for HarnessBudget/StallDetector, checked before the one inference turn v1 takes.
// core-server enforces its own copy of maxTurns/maxTokens/deadlineSeconds server-side regardless
// (see AssignmentResponse) -- this is a local backstop, not the source of truth.
const DEFAULT_MAX_TURNS: u32 = 20;
const DEFAULT_MAX_TOKENS: u64 = 200_000;
const DEFAULT_DEADLINE_SECONDS: u64 = 600;
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
    let budget = HarnessBudget::new(
        DEFAULT_MAX_TURNS,
        DEFAULT_MAX_TOKENS,
        Instant::now() + Duration::from_secs(DEFAULT_DEADLINE_SECONDS),
    );
    let stall_detector = StallDetector::new(DEFAULT_STALL_REPEATS);

    let mut runner = AgentHarnessRunner::new(adapter.clone(), adapter, budget, stall_detector);
    let outcome = runner.run(assignment_id).await?;

    println!("core-agent-harness finished: {outcome:?}");
    Ok(())
}

fn required_env(key: &str) -> Result<String, HarnessError> {
    std::env::var(key).map_err(|_| HarnessError::MissingEnv(key.to_string()))
}
