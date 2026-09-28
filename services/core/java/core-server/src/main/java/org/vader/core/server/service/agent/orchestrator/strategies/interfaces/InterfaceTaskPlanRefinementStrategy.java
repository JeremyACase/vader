package org.vader.core.server.service.agent.orchestrator.strategies.interfaces;

import org.vader.core.server.models.TaskPlanRefinementRequest;
import org.vader.core.server.models.TaskPlanRefinementVerdict;

/**
 * Strategy for critiquing a freshly-decomposed task plan before it is ever persisted.
 *
 * <p>The active implementation is selected by Spring based on the {@code vader.orchestrator.type}
 * property -- the same switch {@code InterfaceLlmOrchestrationStrategy} uses for decomposition.
 * Only ever consulted once a plan has already passed {@code TaskPlanStructuralValidator}.</p>
 */
public interface InterfaceTaskPlanRefinementStrategy {

    /**
     * Critiques a task plan.
     *
     * @param request the plan (and the original request it should serve) to critique
     * @return the verdict
     */
    TaskPlanRefinementVerdict critique(TaskPlanRefinementRequest request);
}
