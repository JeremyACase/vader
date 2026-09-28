package org.vader.core.server.service.agent.orchestrator.strategies;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.vader.core.server.exceptions.OrchestratorUnavailableException;
import org.vader.core.server.models.TaskPlanRefinementRequest;
import org.vader.core.server.models.TaskPlanRefinementVerdict;
import org.vader.core.server.service.agent.orchestrator.strategies.interfaces.InterfaceTaskPlanRefinementStrategy;
import org.vader.core.server.service.llm.LlmRequestQueue;

/**
 * Coordinates the task-plan critique with a local Ollama instance.
 *
 * <p>Active only when {@code vader.orchestrator.type} is {@code local}. Purely a thin proxy, same
 * as {@code LocalReattemptDecisionStrategy}: this enqueues the critique onto
 * {@link LlmRequestQueue} and blocks until whichever replica's inbox claims and processes it
 * writes a response back. It never talks to Ollama directly -- that's
 * {@code TaskPlanRefinementLlmExecutor}, called only from inside the inbox.</p>
 *
 * <p>An unreachable LLM always fails loudly, in every {@code vader.mode}: silently approving an
 * uncritiqued plan defeats the entire point of refinement. The canned approval exists only behind
 * {@link StaticTaskPlanRefinementStrategy}, permitted only in {@code vader.mode=TEST}.</p>
 */
@Service
@ConditionalOnProperty(prefix = "vader.orchestrator", name = "type", havingValue = "local")
public class LocalTaskPlanRefinementStrategy implements InterfaceTaskPlanRefinementStrategy {

    @Autowired
    private LlmRequestQueue requestQueue;

    @Override
    public TaskPlanRefinementVerdict critique(final TaskPlanRefinementRequest request) {
        var outcome = this.requestQueue.submitTaskPlanRefinement(request);
        if (outcome.isUnreachable()) {
            throw new OrchestratorUnavailableException(
                "Could not reach the local LLM to critique the task plan.",
                new IllegalStateException(outcome.unreachableReason()));
        }
        return outcome.verdict();
    }
}
