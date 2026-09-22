package org.vader.core.server.service.agent.evaluator.strategies.interfaces;

import org.vader.core.server.models.EvaluationRequest;
import org.vader.core.server.models.EvaluationVerdict;

/**
 * Strategy for independently judging whether a settled attempt actually succeeded.
 *
 * <p>The active implementation is selected by Spring based on the {@code vader.orchestrator.type}
 * property -- the same switch {@code InterfaceLlmOrchestrationStrategy} uses for decomposition.</p>
 */
public interface InterfaceEvaluatorStrategy {

    /**
     * Judges one settled attempt.
     *
     * @param request the task and attempt outcome to judge
     * @return the verdict
     */
    EvaluationVerdict evaluate(EvaluationRequest request);
}
