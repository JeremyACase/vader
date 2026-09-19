package org.vader.core.server.service.strategies.orchestration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.vader.common.model.vader.dto.ClientPrompt;
import org.vader.common.model.vader.dto.Task;
import org.vader.common.model.vader.dto.TaskGraph;
import org.vader.common.model.vader.dto.TaskPlan;
import org.vader.core.server.exceptions.OrchestratorResponseException;
import org.vader.core.server.exceptions.OrchestratorUnavailableException;
import org.vader.core.server.service.llm.LlmRequestQueue;
import org.vader.core.server.service.strategies.orchestration.interfaces.InterfaceLlmOrchestrationStrategy;

/**
 * Coordinates problem decomposition with a local Ollama instance.
 *
 * <p>Active only when {@code vader.orchestrator.type} is {@code local}, in which case the Helm
 * chart also installs an Ollama deployment and {@code spring.ai.ollama.*} points this client at
 * it.</p>
 *
 * <p>Purely a thin proxy, same as {@code LocalInferenceGatewayStrategy}: this enqueues the
 * decomposition onto {@link LlmRequestQueue} and blocks until whichever replica's inbox claims and
 * processes it writes a response back. It never talks to Ollama directly -- that's
 * {@code DecompositionLlmExecutor}, called only from inside the inbox, offering only tools tagged
 * {@code ORCHESTRATION} (the "higher-level agent" role: never sandbox code execution).</p>
 *
 * <p>Structured output is handled by {@code ChatClient.entity(...)} inside that executor, against
 * the lean {@link LlmTaskPlan} shape (objective + tasks only); this strategy then builds a full
 * {@link TaskPlan} from it and re-serializes to JSON to satisfy the
 * {@link InterfaceLlmOrchestrationStrategy} contract.</p>
 *
 * <p>When the LLM is unreachable and {@code vader.orchestrator.local.fallback-to-static} is
 * {@code true} (the default), this returns the canned {@link StaticTaskPlan} instead of failing,
 * so {@code helm test} and CI pass with no Ollama in the cluster. A reachable LLM that returns
 * an unusable response still fails with {@link OrchestratorResponseException}.</p>
 */
@Service
@ConditionalOnProperty(prefix = "vader.orchestrator", name = "type", havingValue = "local")
public class LocalLlmOrchestrationStrategy implements InterfaceLlmOrchestrationStrategy {

    private static final Logger logger =
        LoggerFactory.getLogger(LocalLlmOrchestrationStrategy.class);

    @Autowired
    private LlmRequestQueue requestQueue;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${vader.orchestrator.local.fallback-to-static:true}")
    private boolean fallbackToStatic;

    @Override
    public String orchestrate(final ClientPrompt clientPrompt) {
        try {
            var outcome = this.requestQueue.submitDecomposition(clientPrompt.getText());
            return outcome.isUnreachable()
                ? this.handleUnreachable(outcome.unreachableReason())
                : this.toTaskPlanJson(outcome.plan());
        } catch (OrchestratorResponseException | OrchestratorUnavailableException e) {
            throw e;
        } catch (JsonProcessingException | RuntimeException e) {
            throw new OrchestratorResponseException(
                "The local LLM's response was not a usable task plan: " + e.getMessage(), e);
        }
    }

    private String handleUnreachable(final String reason) {
        if (this.fallbackToStatic) {
            logger.warn("Local LLM unreachable ({}); returning the static fallback plan.", reason);
            return StaticTaskPlan.JSON;
        }
        throw new OrchestratorUnavailableException(
            "Could not reach the local LLM.", new IllegalStateException(reason));
    }

    private String toTaskPlanJson(final LlmTaskPlan llmPlan) throws JsonProcessingException {
        if (Objects.isNull(llmPlan)
            || Objects.isNull(llmPlan.objective())
            || Objects.isNull(llmPlan.tasks())
            || llmPlan.tasks().isEmpty()) {
            throw new OrchestratorResponseException(
                "The local LLM did not return a usable task plan.");
        }
        return this.objectMapper.writeValueAsString(toTaskPlan(llmPlan));
    }

    private static TaskPlan toTaskPlan(final LlmTaskPlan llmPlan) {
        var taskGraph = new TaskGraph();
        taskGraph.setTasks(llmPlan.tasks().stream().map(source -> {
            var task = new Task();
            task.setTitle(source.title());
            task.setDescription(source.description());
            return task;
        }).toList());

        var taskPlan = new TaskPlan();
        taskPlan.setReasoning(llmPlan.reasoning());
        taskPlan.setObjective(llmPlan.objective());
        taskPlan.setTaskGraph(taskGraph);
        return taskPlan;
    }
}
