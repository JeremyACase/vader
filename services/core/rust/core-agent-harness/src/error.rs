/// Every way a harness run can fail outright -- i.e. never reach a normal
/// [`crate::runner::HarnessOutcome`] to report at all (budget exhaustion and stalling are not
/// here: those are legitimate terminal outcomes, reported via `submit_result` like success or
/// failure, not error conditions).
///
/// Deliberately flat (no nested causes beyond `#[source]`) so a failure can be reported back to
/// `core-server` as a short, stable string.
#[derive(Debug, thiserror::Error)]
pub enum HarnessError {
    #[error("missing required environment variable: {0}")]
    MissingEnv(String),

    #[error("invalid UUID in {field}: {source}")]
    InvalidId {
        field: &'static str,
        #[source]
        source: uuid::Error,
    },

    #[error("core-server control plane is unreachable: {0}")]
    ControlPlaneUnavailable(String),

    #[error("core-server inference gateway is unreachable: {0}")]
    InferenceUnavailable(String),

    #[error("core-server tool-call invocation is unreachable: {0}")]
    ToolInvocationUnavailable(String),
}
