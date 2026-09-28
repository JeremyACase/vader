package org.vader.core.server.service.agent.orchestrator.strategies;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.vader.core.server.models.ReattemptDecisionRequest;

class StaticReattemptDecisionStrategyTest {

    private final StaticReattemptDecisionStrategy strategy =
        new StaticReattemptDecisionStrategy();

    @Test
    void decide_alwaysApprovesRetry() {
        var request = new ReattemptDecisionRequest(
            "title", "description", 1, 3, "it broke", List.of());

        var decision = this.strategy.decide(request);

        assertThat(decision.shouldReattempt()).isTrue();
        assertThat(decision.reasoning()).isNotBlank();
    }
}
