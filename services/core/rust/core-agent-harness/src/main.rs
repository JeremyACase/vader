// Fill-in-progress: `runner::AgentHarnessRunner::run` is a `todo!()` stub, so most of the
// assignment/outcome/budget/stall-detector surface below isn't wired up or called yet. Remove
// this once `run` is implemented and starts exercising it -- `cargo clippy -- -D warnings` will
// then tell you exactly what's still genuinely unused.
#![allow(dead_code)]

mod assignment;
mod budget;
mod control_plane;
mod core_server_adapter;
mod error;
mod inference_gateway;
mod runner;
mod stall_detector;

use std::time::{Duration, Instant};

use assignment::{AssignmentId, TaskId};
use budget::HarnessBudget;
use core_server_adapter::CoreServerAdapter;
use error::HarnessError;
use runner::AgentHarnessRunner;
use stall_detector::StallDetector;

// Placeholder bounds. Once `CoreServerAdapter::fetch_assignment` is implemented, the assignment
// itself (`max_turns` / `max_tokens` / `deadline_seconds`) becomes the source of truth and these
// constants go away.
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
