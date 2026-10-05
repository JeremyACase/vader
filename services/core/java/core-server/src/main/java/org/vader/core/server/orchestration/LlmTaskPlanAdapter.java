package org.vader.core.server.orchestration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.vader.common.model.vader.dto.ClientPrompt;
import org.vader.common.model.vader.dto.Task;
import org.vader.common.model.vader.dto.TaskGraph;
import org.vader.common.model.vader.dto.TaskPlan;
import org.vader.core.server.llm.LlmRequestQueue;
import org.vader.core.server.llm.OrchestratorUnavailableException;
import org.vader.core.server.orchestration.model.AttachedFile;
import org.vader.core.server.orchestration.model.DecompositionRequest;
import org.vader.core.server.orchestration.model.LlmTaskPlan;

/**
 * Asks the orchestrator LLM to decompose a client prompt, then adapts the lean {@link LlmTaskPlan}
 * it returns into a full {@link TaskPlan}, serialized as JSON.
 *
 * <p>The call itself goes through {@link LlmRequestQueue} to {@link DecompositionLlmExecutor},
 * which offers only tools tagged {@code ORCHESTRATION} (the "higher-level agent" role: never
 * sandbox code execution).</p>
 *
 * <p>{@link LlmTaskPlan.LlmTask#dependsOn} names earlier tasks by title, matched by
 * {@link TaskTitleMatcher} and constrained to earlier positions so the graph is acyclic by
 * construction. Each task gets a fresh id here so those titles resolve into
 * {@link Task#getDependsOnTaskIds()} before the plan reaches
 * {@code TaskGraphDtoToEntityMapper}.</p>
 *
 * <p>An unreachable LLM fails with {@link OrchestratorUnavailableException}; a reachable one that
 * returns an unusable plan fails with {@link OrchestratorResponseException}.</p>
 */
@Service
public class LlmTaskPlanAdapter {

    @Autowired
    private LlmRequestQueue requestQueue;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Decomposes a client prompt into a task plan.
     *
     * @param clientPrompt the prompt to decompose, whose text is passed to the model verbatim
     * @param attachedFiles the files attached to the prompt, by name and type only
     * @param revisionGuidance why the previous plan for this same prompt was rejected, for the
     *     model to correct; {@code null} on the first attempt
     * @return the task plan, as JSON
     */
    public String decompose(
            final ClientPrompt clientPrompt, final List<AttachedFile> attachedFiles,
            final String revisionGuidance) {
        try {
            var plan = this.requestQueue.submit(
                DecompositionLlmExecutor.class,
                new DecompositionRequest(
                    clientPrompt.getText(), attachedFiles, revisionGuidance));
            return this.toTaskPlanJson(plan);
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
