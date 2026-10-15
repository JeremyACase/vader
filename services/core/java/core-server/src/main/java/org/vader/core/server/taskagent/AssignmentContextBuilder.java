package org.vader.core.server.taskagent;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.vader.common.model.vader.entity.ClientPromptEntity;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.common.model.vader.entity.TaskUpdateType;
import org.vader.core.server.llm.PromptKey;
import org.vader.core.server.llm.PromptTemplateRegistry;
import org.vader.core.server.llm.PromptVariablesBuilder;
import org.vader.core.server.sandbox.TaskAttemptSandboxService;
import org.vader.core.server.workflow.PlanOutlineBuilder;
import org.vader.core.server.workflow.TaskAttemptRepository;
import org.vader.core.server.workflow.TaskUpdateRepository;

/**
 * Composes the background a task's own short description never carries on its own: the
 * original client-submitted request, any files attached to it, the plan the task is one step of,
 * the larger task a runtime subtask is one step of, the results of any prerequisite tasks, and
 * why any earlier attempts at this same task were rejected -- without this, a task like "ensure
 * the report is well-structured" has no way to discover what report, since the planner never
 * restates it in every subtask.
 */
@Component
public class AssignmentContextBuilder {

    private static final List<TaskUpdateType> FAILED_VERDICTS =
        List.of(TaskUpdateType.FAILED, TaskUpdateType.TIMED_OUT);

    @Autowired
    private TaskAttemptRepository taskAttemptRepository;

    @Autowired
    private TaskUpdateRepository taskUpdateRepository;

    @Autowired
    private PlanOutlineBuilder planOutlineBuilder;

    @Autowired
    private PromptTemplateRegistry promptTemplateRegistry;

    // Absent when the Python sandbox operator is disabled; attached files are then described as
    // readable via get_object_content only, rather than as already in a working directory.
    @Autowired(required = false)
    private TaskAttemptSandboxService taskAttemptSandboxService;

    /**
     * Builds the context for one task about to run.
     *
     * @param task the task about to be dispatched
     * @return the composed context, always at least the original request
     */
    public String build(final TaskEntity task) {
        var clientPrompt =
            task.owningTaskGraph().getTaskPlan().getWorkflow().getClientPrompt();
        var sections = Stream.of(
                this.requestSection(clientPrompt),
                this.attachedFilesSection(clientPrompt),
                this.planSection(task),
                this.parentSection(task),
                this.dependencySection(task),
                this.previousAttemptsSection(task))
            .filter(Objects::nonNull)
            .toList();
        return String.join("\n\n", sections);
    }

    private String requestSection(final ClientPromptEntity clientPrompt) {
        return this.promptTemplateRegistry.render(PromptKey.CONTEXT_REQUEST,
            new PromptVariablesBuilder().with("request", clientPrompt.getText()).build());
    }

    /**
     * The whole plan with this task marked. The request alone reads as this task's to-do list:
     * an agent given "analyze it, then decide if it can be reformatted" will start reformatting
     * in the analysis step unless it can see that another task owns that.
     */
    private String planSection(final TaskEntity task) {
        var lines = this.planOutlineBuilder.build(task).stream()
            .map(step -> step.outlineLine("your task"))
            .toList();
        return this.promptTemplateRegistry.render(PromptKey.CONTEXT_PLAN,
            new PromptVariablesBuilder().with("plan", String.join("\n", lines)).build());
    }

    /**
     * Lists the request's attached files. With the sandbox enabled, each is named exactly as
     * {@link TaskAttemptSandboxService} stages it -- the model is told a file is already in its
     * working directory under that name, never asked to stage anything itself.
     */
    private String attachedFilesSection(final ClientPromptEntity clientPrompt) {
        String result = null;
        if (!clientPrompt.getFiles().isEmpty()) {
            var lines = this.taskAttemptSandboxService == null
                ? this.objectStorageFileLines(clientPrompt)
                : this.workspaceFileLines(clientPrompt);
            result = this.promptTemplateRegistry.render(PromptKey.CONTEXT_ATTACHED_FILES,
                new PromptVariablesBuilder().with("files", String.join("\n", lines)).build());
        }
        return result;
    }

    /**
     * Offers exactly one way in -- {@code run_python_code} -- even for text files: given a second
     * option, a model picks the wrong one for binary files and wastes a turn.
     */
    private List<String> workspaceFileLines(final ClientPromptEntity clientPrompt) {
        return TaskAttemptSandboxService.workspaceFilesFor(clientPrompt).stream()
            .map(file -> this.promptTemplateRegistry.render(PromptKey.CONTEXT_WORKSPACE_FILE,
                new PromptVariablesBuilder()
                    .with("filename", file.filename())
                    .with("contentType", file.contentType())
                    .build()))
            .toList();
    }

    private List<String> objectStorageFileLines(final ClientPromptEntity clientPrompt) {
        return clientPrompt.getFiles().stream()
            .map(file -> this.promptTemplateRegistry.render(PromptKey.CONTEXT_OBJECT_STORAGE_FILE,
                new PromptVariablesBuilder()
                    .with("filename", file.getOriginalFilename())
                    .with("id", file.getId())
                    .with("contentType", file.getContentType())
                    .build()))
            .toList();
    }

    /**
     * For a runtime subtask: the larger task it is one step of, plus that task's incomplete
     * attempt. The attempt usually carries the exact error or code this step has to fix, but it
     * is also exactly the kind of "here is what I'll do" text that must not be mistaken for work
     * done -- hence the framing.
     */
    private String parentSection(final TaskEntity task) {
        String result = null;
        var parent = task.getParentTask();
        if (Objects.nonNull(parent)) {
            var decomposedAttempt = task.getSpawnedByAttempt();
            var partial = Objects.isNull(decomposedAttempt) ? null : decomposedAttempt.getResult();
            result = this.promptTemplateRegistry.render(PromptKey.CONTEXT_PARENT_TASK,
                new PromptVariablesBuilder()
                    .with("title", parent.getTitle())
                    .with("description", parent.getDescription())
                    .with("partialResult", partial)
                    .build());
        }
        return result;
    }

    /**
     * Prerequisite results, framed as data: a model given an earlier task's "let's proceed" text
     * and its code block will otherwise re-run that code as though it were its own task. A
     * subtask also inherits its ancestors' prerequisites, since it continues their work.
     */
    private String dependencySection(final TaskEntity task) {
        String result = null;
        var dependencies = effectiveDependencies(task);
        if (!dependencies.isEmpty()) {
            var lines = dependencies.stream().map(this::dependencyLine).toList();
            result = this.promptTemplateRegistry.render(PromptKey.CONTEXT_DEPENDENCIES,
                new PromptVariablesBuilder()
                    .with("dependencies", String.join("\n", lines))
                    .build());
        }
        return result;
    }

    /**
     * For a reattempt: the reviewer's reason for rejecting each earlier attempt at this task,
     * oldest first. Without it a retry receives exactly the prompt its predecessor did and, but
     * for sampling, repeats it. Only attempts with a failed verdict appear, so the attempt now
     * starting -- and one still awaiting review -- never does.
     */
    private String previousAttemptsSection(final TaskEntity task) {
        String result = null;
        var lines = this.taskAttemptRepository.findByTaskIdOrderByAttemptNumberDesc(task.getId())
            .stream()
            .sorted(Comparator.comparing(TaskAttemptEntity::getAttemptNumber))
            .map(this::previousAttemptLine)
            .flatMap(Optional::stream)
            .toList();
        if (!lines.isEmpty()) {
            result = this.promptTemplateRegistry.render(PromptKey.CONTEXT_PREVIOUS_ATTEMPTS,
                new PromptVariablesBuilder().with("attempts", String.join("\n", lines)).build());
        }
        return result;
    }

    private Optional<String> previousAttemptLine(final TaskAttemptEntity attempt) {
        return this.taskUpdateRepository
            .findFirstByTaskAttemptIdAndTypeInOrderByCreatedAtDesc(attempt.getId(), FAILED_VERDICTS)
            .map(verdict -> this.promptTemplateRegistry.render(PromptKey.CONTEXT_PREVIOUS_ATTEMPT,
                new PromptVariablesBuilder()
                    .with("attemptNumber", attempt.getAttemptNumber())
                    .with("verdict", verdict.getDescription())
                    .build()));
    }

    private static Set<TaskEntity> effectiveDependencies(final TaskEntity task) {
        var dependencies = new LinkedHashSet<TaskEntity>();
        if (Objects.nonNull(task.getParentTask())) {
            dependencies.addAll(effectiveDependencies(task.getParentTask()));
        }
        dependencies.addAll(task.getDependsOn());
        return dependencies;
    }

    private String dependencyLine(final TaskEntity dependency) {
        var result = this.taskAttemptRepository
            .findFirstByTaskIdOrderByAttemptNumberDesc(dependency.getId())
            .map(TaskAttemptEntity::effectiveResult)
            .orElse(null);
        return this.promptTemplateRegistry.render(PromptKey.CONTEXT_DEPENDENCY,
            new PromptVariablesBuilder()
                .with("title", dependency.getTitle())
                .with("result", result)
                .build());
    }
}
