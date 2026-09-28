package org.vader.core.server.service.agent.orchestrator.strategies;

import org.springframework.beans.factory.annotation.Autowired;
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
 * <p>An unreachable LLM always fails loudly, in every {@code vader.mode} -- never a canned
 * "retry" answer. That canned answer exists only behind {@link StaticReattemptDecisionStrategy},
 * permitted only in {@code vader.mode=TEST}.</p>
 */
@Service
@ConditionalOnProperty(prefix = "vader.orchestrator", name = "type", havingValue = "local")
public class LocalReattemptDecisionStrategy implements InterfaceReattemptDecisionStrategy {

    @Autowired
    private LlmRequestQueue requestQueue;

    @Override
    public ReattemptDecision decide(final ReattemptDecisionRequest request) {
        var outcome = this.requestQueue.submitReattemptDecision(request);
        if (outcome.isUnreachable()) {
            throw new OrchestratorUnavailableException(
                "Could not reach the local LLM to decide whether to re-attempt this task.",
                new IllegalStateException(outcome.unreachableReason()));
        }
        return outcome.decision();
    }
}
