package org.vader.core.server.service.agent.orchestrator.strategies;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.vader.common.model.vader.dto.ClientPrompt;
import org.vader.common.model.vader.dto.Task;
import org.vader.common.model.vader.dto.TaskGraph;
import org.vader.common.model.vader.dto.TaskPlan;
import org.vader.core.server.exceptions.OrchestratorResponseException;
import org.vader.core.server.exceptions.OrchestratorUnavailableException;
import org.vader.core.server.models.DecompositionRequest;
import org.vader.core.server.service.agent.orchestrator.TaskTitleMatcher;
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
 * <p>{@link LlmTaskPlan.LlmTask#dependsOn} lets the model declare real dependencies between
 * tasks -- the titles of earlier tasks in its own {@code tasks} array, matched by
 * {@link TaskTitleMatcher} and constrained to reference only earlier positions (making the
 * resulting graph acyclic by construction). Each task is assigned a fresh id here so those titles
 * can be resolved into {@link Task#getDependsOnTaskIds()} before the plan ever reaches
 * {@code TaskGraphDtoToEntityMapper}, which already knows how to wire arbitrary DTO-id-based
 * dependency edges into the persisted {@code TaskEntity} graph -- this strategy's only job is to
 * hand it real edges instead of none.</p>
 *
 * <p>An unreachable LLM always fails loudly with {@link OrchestratorUnavailableException}, in
 * every {@code vader.mode}: a user must never be handed a canned plan for their actual request.
 * The canned plan exists only behind {@link StaticLlmOrchestrationStrategy}, which is itself
 * permitted only in {@code vader.mode=TEST} (see {@code StaticStrategyModeGuard}). A reachable LLM
 * that returns an unusable response fails with {@link OrchestratorResponseException}.</p>
 */
@Service
@ConditionalOnProperty(prefix = "vader.orchestrator", name = "type", havingValue = "local")
public class LocalLlmOrchestrationStrategy implements InterfaceLlmOrchestrationStrategy {

    @Autowired
    private LlmRequestQueue requestQueue;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public String orchestrate(final ClientPrompt clientPrompt, final String revisionGuidance) {
        try {
            var outcome = this.requestQueue.submitDecomposition(
                new DecompositionRequest(clientPrompt.getText(), revisionGuidance));
            if (outcome.isUnreachable()) {
                throw new OrchestratorUnavailableException(
                    "Could not reach the local LLM.",
                    new IllegalStateException(outcome.unreachableReason()));
            }
            return this.toTaskPlanJson(outcome.plan());
        } catch (OrchestratorResponseException | OrchestratorUnavailableException e) {
            throw e;
        } catch (JsonProcessingException | RuntimeException e) {
            throw new OrchestratorResponseException(
                "The local LLM's response was not a usable task plan: " + e.getMessage(), e);
        }
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

    /**
     * Resolves a task's {@code dependsOn} titles to positions of earlier tasks, rejecting any
     * that names no earlier task -- itself, a later task, or nothing at all. Only-earlier is the
     * one rule that keeps the resulting graph acyclic without any cycle detection downstream.
     *
     * @param tasks the model-produced tasks, in the order the model listed them
     * @param taskIndex the task whose dependencies to resolve
     * @return the positions of the tasks it depends on, without duplicates
     */
    private static List<Integer> dependencyIndicesOf(
            final List<LlmTaskPlan.LlmTask> tasks, final int taskIndex) {
        return Objects.requireNonNullElse(tasks.get(taskIndex).dependsOn(), List.<String>of())
            .stream()
            .map(title -> earlierIndexOf(tasks, taskIndex, title))
            .distinct()
            .toList();
    }

    private static int earlierIndexOf(
            final List<LlmTaskPlan.LlmTask> tasks, final int taskIndex, final String title) {
        return IntStream.range(0, taskIndex)
            .filter(index -> TaskTitleMatcher.matches(tasks.get(index).title(), title))
            .findFirst()
            .orElseThrow(() -> new OrchestratorResponseException(
                "The local LLM's task plan has an invalid dependency: task '"
                    + tasks.get(taskIndex).title() + "' depends on '" + title
                    + "', which is not the title of any task listed before it."));
    }

    private static TaskPlan toTaskPlan(final LlmTaskPlan llmPlan) {
        var sourceTasks = llmPlan.tasks();
        var ids = sourceTasks.stream().map(ignored -> UUID.randomUUID().toString()).toList();

        var tasks = new ArrayList<Task>(sourceTasks.size());
        for (var index = 0; index < sourceTasks.size(); index++) {
            tasks.add(toTask(
                sourceTasks.get(index), ids.get(index), dependencyIndicesOf(sourceTasks, index),
                ids));
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
            final LlmTaskPlan.LlmTask source, final String id,
            final List<Integer> dependencyIndices, final List<String> ids) {
        var task = new Task();
        task.setId(id);
        task.setTitle(source.title());
        task.setDescription(source.description());
        task.setDependsOnTaskIds(dependencyIndices.stream().map(ids::get).toList());
        return task;
    }
}
