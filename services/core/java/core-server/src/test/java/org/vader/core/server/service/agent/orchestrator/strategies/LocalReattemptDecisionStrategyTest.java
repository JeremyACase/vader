package org.vader.core.server.service.agent.orchestrator.strategies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.core.server.exceptions.OrchestratorUnavailableException;
import org.vader.core.server.models.ReattemptDecision;
import org.vader.core.server.models.ReattemptDecisionRequest;
import org.vader.core.server.service.llm.LlmRequestQueue;
import org.vader.core.server.service.llm.ReattemptDecisionOutcome;

class LocalReattemptDecisionStrategyTest {

    private static final ReattemptDecisionRequest REQUEST = new ReattemptDecisionRequest(
        "title", "description", 1, 3, "it broke", List.of());

    private LocalReattemptDecisionStrategy strategy(final LlmRequestQueue requestQueue) {
        var strategy = new LocalReattemptDecisionStrategy();
        ReflectionTestUtils.setField(strategy, "requestQueue", requestQueue);
        return strategy;
    }

    @Test
    void decide_returnsTheQueuedDecision() {
        var requestQueue = mock(LlmRequestQueue.class);
        var decision = new ReattemptDecision(false, "will not help");
        when(requestQueue.submitReattemptDecision(REQUEST))
            .thenReturn(new ReattemptDecisionOutcome(decision, null));

        var result = this.strategy(requestQueue).decide(REQUEST);

        assertThat(result).isSameAs(decision);
    }

    @Test
    void decide_whenUnreachable_throwsOrchestratorUnavailable() {
        var requestQueue = mock(LlmRequestQueue.class);
        when(requestQueue.submitReattemptDecision(REQUEST))
            .thenReturn(new ReattemptDecisionOutcome(null, "connection refused"));

        assertThatThrownBy(() -> this.strategy(requestQueue).decide(REQUEST))
            .isInstanceOf(OrchestratorUnavailableException.class)
            .hasMessageContaining("local LLM");
    }
}
