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
import org.vader.core.server.llm.PromptKey;
import org.vader.core.server.llm.PromptTemplateRegistry;
import org.vader.core.server.llm.PromptVariablesBuilder;
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

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Autowired
    private PromptTemplateRegistry promptTemplateRegistry;

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
            .system(this.promptTemplateRegistry.render(PromptKey.PLAN_REFINEMENT_SYSTEM))
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
        var variables = new PromptVariablesBuilder()
            .with("request", request.originalRequestText())
            .with("objective", taskPlan.getObjective())
            .with("tasks", this.describeTasks(taskPlan))
            .build();
        return this.promptTemplateRegistry.render(PromptKey.PLAN_REFINEMENT_USER, variables);
    }

    private String describeTasks(final TaskPlan taskPlan) {
        var tasks = taskPlan.getTaskGraph().getTasks();
        var titlesById = tasks.stream()
            .filter(task -> task.getId() != null)
            .collect(Collectors.toMap(Task::getId, Task::getTitle, (first, second) -> first));
        return IntStream.range(0, tasks.size())
            .mapToObj(index -> this.describeTask(index + 1, tasks.get(index), titlesById))
            .collect(Collectors.joining("\n"));
    }

    private String describeTask(
            final int number, final Task task, final Map<String, String> titlesById) {
        var dependsOn = task.getDependsOnTaskIds().stream()
            .map(id -> "\"" + titlesById.getOrDefault(id, id) + "\"")
            .collect(Collectors.joining(", "));
        var variables = new PromptVariablesBuilder()
            .with("number", number)
            .with("title", task.getTitle())
            .with("description", task.getDescription())
            .with("dependsOn", dependsOn.isEmpty() ? null : dependsOn)
            .build();
        return this.promptTemplateRegistry.render(PromptKey.PLAN_REFINEMENT_TASK, variables);
    }
}
