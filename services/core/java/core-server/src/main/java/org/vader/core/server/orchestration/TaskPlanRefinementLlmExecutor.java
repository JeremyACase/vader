package org.vader.core.server.orchestration;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.vader.common.model.vader.dto.Task;
import org.vader.common.model.vader.dto.TaskPlan;
import org.vader.common.model.vader.entity.LlmRequestKind;
import org.vader.core.server.llm.LlmRequestInbox;
import org.vader.core.server.llm.LlmRequestQueue;
import org.vader.core.server.llm.interfaces.InterfaceLlmExecutor;
import org.vader.core.server.orchestration.model.TaskPlanRefinementRequest;
import org.vader.core.server.orchestration.model.TaskPlanRefinementVerdict;

/**
 * Asks the chat model to critique a freshly-decomposed task plan, via Spring AI's
 * {@link ChatClient}. Called only from inside {@link LlmRequestInbox#handle}; callers submit the
 * request through {@link LlmRequestQueue}.
 *
 * <p>Builds a fresh {@link ChatClient} per call: one shared instance corrupts its advisor chain
 * under concurrent calls (spring-projects/spring-ai#3537). Building one is cheap -- it wraps the
 * already-configured {@code ChatModel}.</p>
 */
@Service
public class TaskPlanRefinementLlmExecutor
    implements InterfaceLlmExecutor<TaskPlanRefinementRequest, TaskPlanRefinementVerdict> {

    private static final String REFINEMENT_INSTRUCTIONS = """
        You are Vader, critiquing a task plan an orchestrator LLM just decomposed from a user's
        request, before it is ever persisted or acted on. The plan has already passed structural
        checks (no dangling dependencies, no dependency cycles, at least one task) -- your job is
        to judge whether it is actually a *good* decomposition of the user's request, not to
        re-check its structure.

        First, check dependencies. For every task, ask: can it really start before the tasks
        it lists under "depends on" have finished -- or does it need something another task
        produces (e.g. analyzing data needs the task that reads it; reviewing a report needs the
        task that writes it)? List every missing dependency in `missingDependencies`, one entry
        per task that must wait: its exact title in `task`, and the exact titles of the tasks it
        must wait for in `dependsOn`. Copy titles exactly as they appear in the plan. Missing
        dependencies are added to the plan automatically -- they are never, on their own, a
        reason to set `needsRevision`.

        Then set `needsRevision` to true only for a problem that needs the plan redone: a task
        that doesn't serve the stated objective at all; two or more tasks that are redundant or
        overlapping; or a plan that is really just one task dressed up as several (or one giant
        task that never really decomposed the request). Do not flag stylistic preferences,
        missing polish, or anything that would just be your own alternative way of doing the
        same job equally well.

        Explain your reasoning in `reasoning` -- when `needsRevision` is true, this reasoning is
        fed back to the orchestrator as the specific guidance for its next attempt, so be
        concrete about what to change.
        """;

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Override
    public LlmRequestKind kind() {
        return LlmRequestKind.TASK_PLAN_REFINEMENT;
    }

    /**
     * Critiques a task plan.
     *
     * @param request the plan (and the original request it should serve) to critique
     * @return the critique
     */
    @Override
    public TaskPlanRefinementVerdict execute(final TaskPlanRefinementRequest request) {
        return this.chatClientBuilder.build().prompt()
            .system(REFINEMENT_INSTRUCTIONS)
            .user(this.userPromptFor(request))
            .call()
            .entity(TaskPlanRefinementVerdict.class);
    }

    /**
     * Shows the plan as a numbered list of titled tasks, each with the titles it depends on, not
     * as raw JSON with opaque UUIDs: the critic answers in titles, so it is shown titles.
     */
    private String userPromptFor(final TaskPlanRefinementRequest request) {
        var taskPlan = request.taskPlan();
        return """
            The user's original request:
            %s

            The plan's objective: %s

            The plan's tasks, in order:
            %s
            """.formatted(
                request.originalRequestText(), taskPlan.getObjective(), describeTasks(taskPlan));
    }

    private static String describeTasks(final TaskPlan taskPlan) {
        var tasks = taskPlan.getTaskGraph().getTasks();
        var titlesById = tasks.stream()
            .filter(task -> task.getId() != null)
            .collect(Collectors.toMap(Task::getId, Task::getTitle, (first, second) -> first));
        return IntStream.range(0, tasks.size())
            .mapToObj(index -> describeTask(index + 1, tasks.get(index), titlesById))
            .collect(Collectors.joining("\n"));
    }

    private static String describeTask(
            final int number, final Task task, final Map<String, String> titlesById) {
        var dependsOn = task.getDependsOnTaskIds().stream()
            .map(id -> "\"" + titlesById.getOrDefault(id, id) + "\"")
            .collect(Collectors.joining(", "));
        return number + ". \"" + task.getTitle() + "\" -- " + task.getDescription()
            + "\n   depends on: " + (dependsOn.isEmpty() ? "nothing" : dependsOn);
    }
}
