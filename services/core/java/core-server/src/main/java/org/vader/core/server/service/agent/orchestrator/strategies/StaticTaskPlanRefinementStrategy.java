package org.vader.core.server.service.agent.orchestrator.strategies;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.vader.core.server.models.TaskPlanRefinementRequest;
import org.vader.core.server.models.TaskPlanRefinementVerdict;
import org.vader.core.server.service.agent.orchestrator.strategies.interfaces.InterfaceTaskPlanRefinementStrategy;

/**
 * Always approves the plan as-is, without calling any LLM.
 *
 * <p>Active when {@code vader.orchestrator.type} is {@code static}, for the same reason
 * {@code StaticReattemptDecisionStrategy} exists: the canned static plan is already known-good, so
 * a deterministic approval keeps the Helm test hook and CI passing without deploying Ollama.</p>
 */
@Service
@ConditionalOnProperty(prefix = "vader.orchestrator", name = "type", havingValue = "static")
public class StaticTaskPlanRefinementStrategy implements InterfaceTaskPlanRefinementStrategy {

    @Override
    public TaskPlanRefinementVerdict critique(final TaskPlanRefinementRequest request) {
        return new TaskPlanRefinementVerdict(false, "Static refinement policy: always approved.");
    }
}
