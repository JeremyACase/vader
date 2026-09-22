package org.vader.core.server.service.agent.orchestrator.strategies;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.vader.core.server.exceptions.OrchestratorUnavailableException;
import org.vader.core.server.models.ReattemptDecision;
import org.vader.core.server.models.ReattemptDecisionRequest;
import org.vader.core.server.service.agent.orchestrator.strategies.interfaces.InterfaceReattemptDecisionStrategy;
import org.vader.core.server.service.llm.LlmRequestQueue;

/**
 * Coordinates the reattempt decision with a local Ollama instance.
 *
 * <p>Active only when {@code vader.orchestrator.type} is {@code local}. Purely a thin proxy, same
 * as {@code LocalLlmOrchestrationStrategy}: this enqueues the decision onto
 * {@link LlmRequestQueue} and blocks until whichever replica's inbox claims and processes it
 * writes a response back. It never talks to Ollama directly -- that's
 * {@code ReattemptDecisionLlmExecutor}, called only from inside the inbox.</p>
 *
 * <p>When the LLM is unreachable and {@code vader.orchestrator.local.fallback-to-static} is
 * {@code true} (the default), this falls back to approving the retry, same as
 * {@link StaticReattemptDecisionStrategy} -- so {@code helm test} and CI pass with no Ollama in
 * the cluster. A reachable LLM that returns nothing usable still fails loudly.</p>
 */
@Service
@ConditionalOnProperty(prefix = "vader.orchestrator", name = "type", havingValue = "local")
public class LocalReattemptDecisionStrategy implements InterfaceReattemptDecisionStrategy {

    private static final Logger logger =
        LoggerFactory.getLogger(LocalReattemptDecisionStrategy.class);

    @Autowired
    private LlmRequestQueue requestQueue;

    @Value("${vader.orchestrator.local.fallback-to-static:true}")
    private boolean fallbackToStatic;

    @Override
    public ReattemptDecision decide(final ReattemptDecisionRequest request) {
        var outcome = this.requestQueue.submitReattemptDecision(request);
        return outcome.isUnreachable()
            ? this.handleUnreachable(outcome.unreachableReason())
            : outcome.decision();
    }

    private ReattemptDecision handleUnreachable(final String reason) {
        if (this.fallbackToStatic) {
            logger.warn("Local LLM unreachable ({}); defaulting to retry.", reason);
            return new ReattemptDecision(
                true, "Local LLM unreachable; defaulting to retry.");
        }
        throw new OrchestratorUnavailableException(
            "Could not reach the local LLM to decide whether to re-attempt this task.",
            new IllegalStateException(reason));
    }
}
