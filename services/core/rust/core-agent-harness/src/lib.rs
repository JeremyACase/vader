// The harness runs on a current-thread runtime and never sends a future across threads, so the
// `Send` bound this lint asks public async traits to spell out is never needed.
#![allow(async_fn_in_trait)]

pub mod assignment;
pub mod budget;
pub mod control_plane;
pub mod conversation;
pub mod core_server_adapter;
pub mod error;
pub mod harness_outcome;
pub mod inference_gateway;
pub mod prompts;
pub mod runner;
pub mod stall_detector;
pub mod tool_executor;
pub mod unfinished_answer_guard;
pub mod wire;
