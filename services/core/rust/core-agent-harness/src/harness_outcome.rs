/// Reported as the failure reason when a turn is cut off at the output token cap. Failing the
/// attempt outright, rather than nudging, is deliberate: at temperature 0 the same conversation
/// just runs into the same cap again, and a clear reason lets the reattempt decision (and a human
/// reading the task) see exactly what went wrong instead of an empty reply or a generic stall.
pub const OUTPUT_CAP_FAILURE_REASON: &str = "The model's reply was cut off at the output token \
    cap before it finished, so its answer or tool call was incomplete and could not be used.";

/// Reported alongside [`HarnessOutcome::TimedOut`].
pub const TIMED_OUT_REASON: &str = "The harness's deadline elapsed before it finished.";

/// Reported alongside [`HarnessOutcome::Stalled`].
pub const STALLED_REASON: &str = "The harness repeated the same action with no progress.";

/// The terminal result of one harness run, reported back to `core-server` via
/// [`crate::control_plane::ControlPlane::submit_result`].
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum HarnessOutcome {
    Succeeded { output: String },
    Failed { reason: String },
    TimedOut,
    Stalled,
}

impl HarnessOutcome {
    /// The outcome of a run whose reply was cut off at the output token cap.
    pub fn cut_off() -> Self {
        Self::Failed {
            reason: OUTPUT_CAP_FAILURE_REASON.to_string(),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn failure_reasons_have_no_runs_of_spaces_from_line_wrapping() {
        [OUTPUT_CAP_FAILURE_REASON, TIMED_OUT_REASON, STALLED_REASON]
            .iter()
            .for_each(|reason| assert!(!reason.contains("  "), "{reason:?}"));
    }
}
