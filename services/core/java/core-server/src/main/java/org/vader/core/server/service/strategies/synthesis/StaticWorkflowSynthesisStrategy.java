package org.vader.core.server.service.strategies.synthesis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.vader.core.server.models.TaskOutcome;
import org.vader.core.server.models.WorkflowSynthesisRequest;
import org.vader.core.server.service.strategies.synthesis.interfaces.InterfaceWorkflowSynthesisStrategy;

/**
 * Returns a fixed-shape summary, counting successes, without calling any LLM.
 *
 * <p>Active when {@code vader.orchestrator.type} is {@code static}, for the same reason
 * {@code StaticLlmOrchestrationStrategy} exists: a deterministic result the Helm test hook and CI
 * can assert against without deploying Ollama.</p>
 */
@Service
@ConditionalOnProperty(prefix = "vader.orchestrator", name = "type", havingValue = "static")
public class StaticWorkflowSynthesisStrategy implements InterfaceWorkflowSynthesisStrategy {

    @Override
    public String synthesize(final WorkflowSynthesisRequest request) {
        var succeededCount = request.taskOutcomes().stream().filter(TaskOutcome::succeeded).count();
        return "Completed " + succeededCount + " of " + request.taskOutcomes().size()
            + " tasks for: " + request.objective();
    }
}
