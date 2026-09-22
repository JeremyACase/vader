package org.vader.core.server.service.agent.evaluator.strategies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.model.vader.entity.TaskAttemptStatus;
import org.vader.core.server.exceptions.OrchestratorUnavailableException;
import org.vader.core.server.models.EvaluationRequest;
import org.vader.core.server.models.EvaluationVerdict;
import org.vader.core.server.service.llm.EvaluationOutcome;
import org.vader.core.server.service.llm.LlmRequestQueue;

class LocalEvaluatorStrategyTest {

    private static final EvaluationRequest REQUEST = new EvaluationRequest(
        "title", "description", TaskAttemptStatus.SUCCEEDED, "result", null, List.of());

    private LocalEvaluatorStrategy strategy(
            final LlmRequestQueue requestQueue, final boolean fallbackToStatic) {
        var strategy = new LocalEvaluatorStrategy();
        ReflectionTestUtils.setField(strategy, "requestQueue", requestQueue);
        ReflectionTestUtils.setField(strategy, "fallbackToStatic", fallbackToStatic);
        return strategy;
    }

    @Test
    void evaluate_returnsTheQueuedVerdict() {
        var requestQueue = mock(LlmRequestQueue.class);
        var verdict = new EvaluationVerdict(true, "looks correct");
        when(requestQueue.submitEvaluation(REQUEST))
            .thenReturn(new EvaluationOutcome(verdict, null));

        var result = this.strategy(requestQueue, true).evaluate(REQUEST);

        assertThat(result).isSameAs(verdict);
    }

    @Test
    void evaluate_whenUnreachableAndFallbackEnabled_trustsSelfReportedStatus() {
        var requestQueue = mock(LlmRequestQueue.class);
        when(requestQueue.submitEvaluation(REQUEST))
            .thenReturn(new EvaluationOutcome(null, "connection refused"));

        var result = this.strategy(requestQueue, true).evaluate(REQUEST);

        assertThat(result.passed()).isTrue();
    }

    @Test
    void evaluate_whenUnreachableAndFallbackDisabled_throwsOrchestratorUnavailable() {
        var requestQueue = mock(LlmRequestQueue.class);
        when(requestQueue.submitEvaluation(REQUEST))
            .thenReturn(new EvaluationOutcome(null, "connection refused"));

        assertThatThrownBy(() -> this.strategy(requestQueue, false).evaluate(REQUEST))
            .isInstanceOf(OrchestratorUnavailableException.class)
            .hasMessageContaining("local LLM");
    }
}
