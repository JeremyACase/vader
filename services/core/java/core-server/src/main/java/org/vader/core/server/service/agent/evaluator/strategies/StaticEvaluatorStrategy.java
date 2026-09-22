package org.vader.core.server.service.agent.evaluator.strategies;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.vader.common.model.vader.entity.TaskAttemptStatus;
import org.vader.core.server.models.EvaluationRequest;
import org.vader.core.server.models.EvaluationVerdict;
import org.vader.core.server.service.agent.evaluator.strategies.interfaces.InterfaceEvaluatorStrategy;

/**
 * Trusts the harness's own self-reported status verbatim, without calling any LLM.
 *
 * <p>Active when {@code vader.orchestrator.type} is {@code static}, for the same reason
 * {@code StaticLlmOrchestrationStrategy} exists: a deterministic result the Helm test hook and CI
 * can assert against without deploying Ollama. This preserves the pre-evaluator behavior of
 * trusting a {@code SUCCEEDED}/{@code FAILED} report at face value.</p>
 */
@Service
@ConditionalOnProperty(prefix = "vader.orchestrator", name = "type", havingValue = "static")
public class StaticEvaluatorStrategy implements InterfaceEvaluatorStrategy {

    @Override
    public EvaluationVerdict evaluate(final EvaluationRequest request) {
        var passed = request.attemptStatus() == TaskAttemptStatus.SUCCEEDED;
        return new EvaluationVerdict(
            passed, "Static evaluator: trusting the self-reported status verbatim.");
    }
}
