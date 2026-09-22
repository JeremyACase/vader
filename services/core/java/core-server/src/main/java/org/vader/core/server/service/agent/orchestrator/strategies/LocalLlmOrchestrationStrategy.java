package org.vader.core.server.service.agent.orchestrator.strategies;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
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
import org.vader.core.server.service.agent.orchestrator.strategies.interfaces.InterfaceLlmOrchestrationStrategy;
import org.vader.core.server.service.llm.LlmRequestQueue;

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
 * <p>{@link LlmTaskPlan.LlmTask#dependsOnIndices} lets the model declare real dependencies
 * between tasks -- indices into its own {@code tasks} array, constrained to reference only
 * earlier positions (validated by {@link #validateDependencies}, making the resulting graph
 * acyclic by construction). Each task is assigned a fresh id here so those indices can be
 * resolved into {@link Task#getDependsOnTaskIds()} before the plan ever reaches
 * {@code TaskGraphDtoToEntityMapper}, which already knows how to wire arbitrary DTO-id-based
 * dependency edges into the persisted {@code TaskEntity} graph -- this strategy's only job is to
 * hand it real edges instead of none.</p>
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
        validateDependencies(llmPlan.tasks());
        return this.objectMapper.writeValueAsString(toTaskPlan(llmPlan));
    }

    /**
     * Rejects any task whose {@code dependsOnIndices} is out of range or references itself or a
     * later task -- the one rule that keeps the resulting dependency graph acyclic without any
     * cycle-detection downstream.
     *
     * @param tasks the model-produced tasks, in the order the model listed them
     */
    private static void validateDependencies(final List<LlmTaskPlan.LlmTask> tasks) {
        for (var index = 0; index < tasks.size(); index++) {
            validateTaskDependencies(tasks, index);
        }
    }

    private static void validateTaskDependencies(
            final List<LlmTaskPlan.LlmTask> tasks, final int taskIndex) {
        var dependsOn = Objects.requireNonNullElse(
            tasks.get(taskIndex).dependsOnIndices(), List.<Integer>of());
        for (var dependencyIndex : dependsOn) {
            if (Objects.isNull(dependencyIndex) || dependencyIndex < 0
                    || dependencyIndex >= taskIndex) {
                throw new OrchestratorResponseException(
                    "The local LLM's task plan has an invalid dependency: task " + taskIndex
                        + " ('" + tasks.get(taskIndex).title() + "') depends on index "
                        + dependencyIndex + ", which must refer to an earlier task.");
            }
        }
    }

    private static TaskPlan toTaskPlan(final LlmTaskPlan llmPlan) {
        var sourceTasks = llmPlan.tasks();
        var ids = sourceTasks.stream().map(ignored -> UUID.randomUUID().toString()).toList();

        var tasks = new ArrayList<Task>(sourceTasks.size());
        for (var index = 0; index < sourceTasks.size(); index++) {
            tasks.add(toTask(sourceTasks.get(index), ids, index));
        }

        var taskGraph = new TaskGraph();
        taskGraph.setTasks(tasks);

        var taskPlan = new TaskPlan();
        taskPlan.setReasoning(llmPlan.reasoning());
        taskPlan.setObjective(llmPlan.objective());
        taskPlan.setTaskGraph(taskGraph);
        return taskPlan;
    }

    private static Task toTask(
            final LlmTaskPlan.LlmTask source, final List<String> ids, final int index) {
        var task = new Task();
        task.setId(ids.get(index));
        task.setTitle(source.title());
        task.setDescription(source.description());
        var dependsOn = Objects.requireNonNullElse(source.dependsOnIndices(), List.<Integer>of());
        task.setDependsOnTaskIds(dependsOn.stream().map(ids::get).toList());
        return task;
    }
}
