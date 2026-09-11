/// Every way a harness run can fail to produce a normal [`crate::runner::HarnessOutcome`].
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

    #[error("harness exceeded its turn/token/deadline budget")]
    BudgetExhausted,

    #[error("harness appears stalled: the same action repeated too many times")]
    Stalled,
}
