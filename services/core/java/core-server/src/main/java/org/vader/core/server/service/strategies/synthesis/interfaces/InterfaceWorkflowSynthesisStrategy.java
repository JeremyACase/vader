package org.vader.core.server.service.strategies.synthesis.interfaces;

import org.vader.core.server.models.WorkflowSynthesisRequest;

/**
 * Strategy for writing a workflow's final answer once every one of its tasks has settled.
 *
 * <p>The active implementation is selected by Spring based on the {@code vader.orchestrator.type}
 * property -- the same switch {@code InterfaceLlmOrchestrationStrategy} uses for decomposition.</p>
 */
public interface InterfaceWorkflowSynthesisStrategy {

    /**
     * Writes one coherent final answer from every task's own outcome.
     *
     * @param request the original prompt, the plan's objective, and every task's outcome
     * @return the synthesized final answer
     */
    String synthesize(WorkflowSynthesisRequest request);
}
