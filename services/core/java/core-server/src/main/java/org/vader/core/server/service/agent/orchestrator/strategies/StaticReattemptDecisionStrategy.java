package org.vader.core.server.service.agent.orchestrator.strategies;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.vader.core.server.models.ReattemptDecision;
import org.vader.core.server.models.ReattemptDecisionRequest;
import org.vader.core.server.service.agent.orchestrator.strategies.interfaces.InterfaceReattemptDecisionStrategy;

/**
 * Always approves a retry, without calling any LLM.
 *
 * <p>Active when {@code vader.orchestrator.type} is {@code static}, for the same reason
 * {@code StaticLlmOrchestrationStrategy} exists: a deterministic result the Helm test hook and CI
 * can assert against without deploying Ollama. This preserves the pre-orchestrator behavior of
 * retrying a failed task until the attempt cap (checked by the caller before this strategy is
 * even consulted) is reached.</p>
 */
@Service
@ConditionalOnProperty(prefix = "vader.orchestrator", name = "type", havingValue = "static")
public class StaticReattemptDecisionStrategy implements InterfaceReattemptDecisionStrategy {

    @Override
    public ReattemptDecision decide(final ReattemptDecisionRequest request) {
        return new ReattemptDecision(
            true, "Static reattempt policy: retry while attempts remain.");
    }
}
