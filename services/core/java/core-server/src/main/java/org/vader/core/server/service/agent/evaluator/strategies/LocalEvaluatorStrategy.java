package org.vader.core.server.service.agent.evaluator.strategies;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.vader.common.model.vader.entity.TaskAttemptStatus;
import org.vader.core.server.exceptions.OrchestratorUnavailableException;
import org.vader.core.server.models.EvaluationRequest;
import org.vader.core.server.models.EvaluationVerdict;
import org.vader.core.server.service.agent.evaluator.strategies.interfaces.InterfaceEvaluatorStrategy;
import org.vader.core.server.service.llm.LlmRequestQueue;

/**
 * Coordinates attempt evaluation with a local Ollama instance.
 *
 * <p>Active only when {@code vader.orchestrator.type} is {@code local}. Purely a thin proxy, same
 * as {@code LocalLlmOrchestrationStrategy}: this enqueues the evaluation onto
 * {@link LlmRequestQueue} and blocks until whichever replica's inbox claims and processes it
 * writes a response back. It never talks to Ollama directly -- that's
 * {@code EvaluationLlmExecutor}, called only from inside the inbox.</p>
 *
 * <p>When the LLM is unreachable and {@code vader.orchestrator.local.fallback-to-static} is
 * {@code true} (the default), this falls back to trusting the self-reported status verbatim,
 * same as {@link StaticEvaluatorStrategy} -- so {@code helm test} and CI pass with no Ollama in
 * the cluster. A reachable LLM that returns nothing usable still fails loudly.</p>
 */
@Service
@ConditionalOnProperty(prefix = "vader.orchestrator", name = "type", havingValue = "local")
public class LocalEvaluatorStrategy implements InterfaceEvaluatorStrategy {

    private static final Logger logger = LoggerFactory.getLogger(LocalEvaluatorStrategy.class);

    @Autowired
    private LlmRequestQueue requestQueue;

    @Value("${vader.orchestrator.local.fallback-to-static:true}")
    private boolean fallbackToStatic;

    @Override
    public EvaluationVerdict evaluate(final EvaluationRequest request) {
        var outcome = this.requestQueue.submitEvaluation(request);
        return outcome.isUnreachable()
            ? this.handleUnreachable(request, outcome.unreachableReason())
            : outcome.verdict();
    }

    private EvaluationVerdict handleUnreachable(
            final EvaluationRequest request, final String reason) {
        if (this.fallbackToStatic) {
            logger.warn("Local LLM unreachable ({}); trusting the self-reported status.", reason);
            var passed = request.attemptStatus() == TaskAttemptStatus.SUCCEEDED;
            return new EvaluationVerdict(
                passed, "Local LLM unreachable; trusting the self-reported status verbatim.");
        }
        throw new OrchestratorUnavailableException(
            "Could not reach the local LLM to evaluate this attempt.",
            new IllegalStateException(reason));
    }
}
