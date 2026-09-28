package org.vader.core.server.service.agent.evaluator.strategies;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.vader.common.model.vader.entity.TaskAttemptStatus;
import org.vader.core.server.models.EvaluationRequest;

class StaticEvaluatorStrategyTest {

    private final StaticEvaluatorStrategy strategy = new StaticEvaluatorStrategy();

    @Test
    void evaluate_forSucceededAttempt_passes() {
        var request = new EvaluationRequest(
            "title", "description", TaskAttemptStatus.SUCCEEDED, "result", null, List.of());

        var verdict = this.strategy.evaluate(request);

        assertThat(verdict.passed()).isTrue();
        assertThat(verdict.reasoning()).isNotBlank();
    }

    @Test
    void evaluate_forFailedAttempt_doesNotPass() {
        var request = new EvaluationRequest(
            "title", "description", TaskAttemptStatus.FAILED, null, "boom", List.of());

        var verdict = this.strategy.evaluate(request);

        assertThat(verdict.passed()).isFalse();
        assertThat(verdict.reasoning()).isNotBlank();
    }
}
