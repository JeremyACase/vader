use std::collections::VecDeque;

/// Catches a harness looping without making progress: the same action fingerprint (e.g. tool
/// name + arguments) repeated too many turns in a row.
///
/// This is a *local*, fast backstop — `core-server` enforces the same idea server-side from the
/// transcripts it logs, independent of whether this code runs correctly.
pub struct StallDetector {
    max_repeats: u32,
    history: VecDeque<String>,
}

impl StallDetector {
    pub fn new(max_repeats: u32) -> Self {
        Self {
            max_repeats,
            history: VecDeque::with_capacity(max_repeats as usize),
        }
    }

    /// Records the fingerprint of the action just taken.
    pub fn record(&mut self, action_fingerprint: String) {
        self.history.push_back(action_fingerprint);
        while self.history.len() > self.max_repeats as usize {
            self.history.pop_front();
        }
    }

    /// True once the most recent `max_repeats` recorded actions are all identical.
    pub fn is_stalled(&self) -> bool {
        let window = self.max_repeats as usize;
        if window == 0 || self.history.len() < window {
            return false;
        }
        let latest = self.history.back().expect("length checked above");
        self.history
            .iter()
            .rev()
            .take(window)
            .all(|action| action == latest)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn is_not_stalled_with_no_history() {
        let stall_detector = StallDetector::new(3);
        assert!(!stall_detector.is_stalled());
    }

    #[test]
    fn is_not_stalled_when_actions_vary() {
        let mut stall_detector = StallDetector::new(3);
        stall_detector.record("read_file(a)".to_string());
        stall_detector.record("read_file(b)".to_string());
        stall_detector.record("read_file(c)".to_string());
        assert!(!stall_detector.is_stalled());
    }

    #[test]
    fn is_stalled_once_the_window_repeats_identically() {
        let mut stall_detector = StallDetector::new(3);
        stall_detector.record("read_file(a)".to_string());
        stall_detector.record("read_file(a)".to_string());
        stall_detector.record("read_file(a)".to_string());
        assert!(stall_detector.is_stalled());
    }

    #[test]
    fn a_varied_action_resets_the_window() {
        let mut stall_detector = StallDetector::new(3);
        stall_detector.record("read_file(a)".to_string());
        stall_detector.record("read_file(a)".to_string());
        stall_detector.record("read_file(b)".to_string());
        assert!(!stall_detector.is_stalled());
    }
}
