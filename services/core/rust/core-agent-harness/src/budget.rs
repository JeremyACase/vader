use std::time::Instant;

/// Tracks the three independent hard limits a harness run must respect: turn count, token
/// spend, and wall-clock deadline. The [`crate::runner::AgentHarnessRunner`] loop consults
/// [`Self::is_exhausted`] after every turn; any one limit tripping ends the run.
pub struct HarnessBudget {
    max_turns: u32,
    max_tokens: u64,
    deadline: Instant,
    turns_used: u32,
    tokens_used: u64,
}

impl HarnessBudget {
    pub fn new(max_turns: u32, max_tokens: u64, deadline: Instant) -> Self {
        Self {
            max_turns,
            max_tokens,
            deadline,
            turns_used: 0,
            tokens_used: 0,
        }
    }

    /// Records one completed turn and how many tokens it spent.
    pub fn record_turn(&mut self, tokens_spent: u64) {
        self.turns_used += 1;
        self.tokens_used += tokens_spent;
    }

    /// True once the turn cap, token cap, or deadline has been reached.
    pub fn is_exhausted(&self) -> bool {
        self.turns_used >= self.max_turns
            || self.tokens_used >= self.max_tokens
            || Instant::now() >= self.deadline
    }

    pub fn turns_used(&self) -> u32 {
        self.turns_used
    }

    pub fn tokens_used(&self) -> u64 {
        self.tokens_used
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::time::Duration;

    fn budget(max_turns: u32, max_tokens: u64) -> HarnessBudget {
        HarnessBudget::new(
            max_turns,
            max_tokens,
            Instant::now() + Duration::from_secs(60),
        )
    }

    #[test]
    fn is_not_exhausted_before_any_turn() {
        let harness_budget = budget(3, 1_000);
        assert!(!harness_budget.is_exhausted());
    }

    #[test]
    fn is_exhausted_once_turn_cap_is_reached() {
        let mut harness_budget = budget(2, 1_000);
        harness_budget.record_turn(10);
        harness_budget.record_turn(10);
        assert!(harness_budget.is_exhausted());
    }

    #[test]
    fn is_exhausted_once_token_cap_is_reached() {
        let mut harness_budget = budget(10, 100);
        harness_budget.record_turn(100);
        assert!(harness_budget.is_exhausted());
    }

    #[test]
    fn is_exhausted_once_deadline_has_passed() {
        let harness_budget = HarnessBudget::new(10, 1_000, Instant::now() - Duration::from_secs(1));
        assert!(harness_budget.is_exhausted());
    }
}
