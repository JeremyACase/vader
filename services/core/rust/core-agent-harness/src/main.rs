use vader_core_agent_harness::HarnessError;

#[tokio::main(flavor = "current_thread")]
async fn main() -> Result<(), HarnessError> {
    // Defaults to "info" -- matching core-server's own default (logging.vader: INFO) -- when
    // RUST_LOG is unset, rather than env_logger's own default of "warn" (which would print
    // nothing at all in the common case, unlike every other Vader service).
    env_logger::Builder::from_env(env_logger::Env::default().default_filter_or("info")).init();
    vader_core_agent_harness::run_from_env().await
}
