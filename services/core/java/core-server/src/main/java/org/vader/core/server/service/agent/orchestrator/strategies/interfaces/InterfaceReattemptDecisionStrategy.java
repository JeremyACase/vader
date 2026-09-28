package org.vader.core.server.service.agent.orchestrator.strategies.interfaces;

import org.vader.core.server.models.ReattemptDecision;
import org.vader.core.server.models.ReattemptDecisionRequest;

/**
 * Strategy for deciding whether a failed task is worth re-attempting.
 *
 * <p>The active implementation is selected by Spring based on the {@code vader.orchestrator.type}
 * property -- the same switch {@code InterfaceLlmOrchestrationStrategy} uses for decomposition.
 * Only ever consulted within remaining attempt budget -- the attempt-count cap is checked before
 * a request is even built.</p>
 */
public interface InterfaceReattemptDecisionStrategy {

    /**
     * Decides whether a failed task is worth re-attempting.
     *
     * @param request the failed task's context
     * @return the decision
     */
    ReattemptDecision decide(ReattemptDecisionRequest request);
}
