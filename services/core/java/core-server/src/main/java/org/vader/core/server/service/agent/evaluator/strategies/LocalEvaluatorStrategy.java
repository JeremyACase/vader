package org.vader.core.server.service.agent.evaluator.strategies;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
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
 * <p>An unreachable LLM always fails loudly, in every {@code vader.mode}: trusting a
 * self-reported status without independent evaluation is exactly the failure mode evaluation
 * exists to prevent. That trusting answer exists only behind {@link StaticEvaluatorStrategy},
 * permitted only in {@code vader.mode=TEST}.</p>
 */
@Service
@ConditionalOnProperty(prefix = "vader.orchestrator", name = "type", havingValue = "local")
public class LocalEvaluatorStrategy implements InterfaceEvaluatorStrategy {

    @Autowired
    private LlmRequestQueue requestQueue;

    @Override
    public EvaluationVerdict evaluate(final EvaluationRequest request) {
        var outcome = this.requestQueue.submitEvaluation(request);
        if (outcome.isUnreachable()) {
            throw new OrchestratorUnavailableException(
                "Could not reach the local LLM to evaluate this attempt.",
                new IllegalStateException(outcome.unreachableReason()));
        }
        return outcome.verdict();
    }
}
