package org.vader.core.server.llm;

import java.util.Set;
import org.vader.common.model.vader.entity.LlmRequestKind;

/**
 * Every prompt template core-server sends a model, each a {@code .st} file under
 * {@code prompts/}. A template belongs to the one {@link LlmRequestKind} whose calls it shapes,
 * so a call kind's fingerprint changes whenever any of its templates does.
 *
 * <p>Declares each template's variables, and {@link PromptTemplateRegistry} refuses to start if
 * a template references any other: a template edited out of step with the code that fills it
 * fails at startup rather than on some later LLM call.</p>
 */
public enum PromptKey {

    DECOMPOSITION(LlmRequestKind.DECOMPOSITION, "orchestration/decomposition",
        "attachedFiles", "revisionGuidance"),
    PLAN_REFINEMENT_SYSTEM(LlmRequestKind.TASK_PLAN_REFINEMENT,
        "orchestration/plan-refinement-system"),
    PLAN_REFINEMENT_USER(LlmRequestKind.TASK_PLAN_REFINEMENT, "orchestration/plan-refinement-user",
        "request", "objective", "tasks"),
    PLAN_REFINEMENT_TASK(LlmRequestKind.TASK_PLAN_REFINEMENT, "orchestration/plan-refinement-task",
        "number", "title", "description", "dependsOn"),

    EVALUATION_SYSTEM(LlmRequestKind.EVALUATION, "review/evaluation-system"),
    EVALUATION_USER(LlmRequestKind.EVALUATION, "review/evaluation-user",
        "userRequest", "planObjective", "plan", "taskTitle", "taskDescription", "succeeded",
        "result", "failureReason", "toolEvidence", "uploadedFiles"),
    REATTEMPT_DECISION_SYSTEM(LlmRequestKind.REATTEMPT_DECISION,
        "review/reattempt-decision-system"),
    REATTEMPT_DECISION_USER(LlmRequestKind.REATTEMPT_DECISION, "review/reattempt-decision-user",
        "taskTitle", "taskDescription", "attemptNumber", "maxAttempts", "failureReasoning",
        "priorUpdates"),

    SYNTHESIS(LlmRequestKind.WORKFLOW_SYNTHESIS, "workflow/synthesis",
        "request", "objective", "taskSummaries", "files"),
    SYNTHESIS_TASK(LlmRequestKind.WORKFLOW_SYNTHESIS, "workflow/synthesis-task",
        "title", "succeeded", "output"),
    SYNTHESIS_FILE(LlmRequestKind.WORKFLOW_SYNTHESIS, "workflow/synthesis-file",
        "filename", "content"),

    TASK_SYSTEM(LlmRequestKind.INFERENCE_TURN, "taskagent/task-system"),
    TASK_MESSAGE(LlmRequestKind.INFERENCE_TURN, "taskagent/task-message", "context", "objective"),
    EMPTY_REPLY_NUDGE(LlmRequestKind.INFERENCE_TURN, "taskagent/empty-reply-nudge"),
    UNEXECUTED_CODE_NUDGE(LlmRequestKind.INFERENCE_TURN, "taskagent/unexecuted-code-nudge"),
    UNRESOLVED_ERROR_NUDGE(LlmRequestKind.INFERENCE_TURN, "taskagent/unresolved-error-nudge"),
    CONTEXT_REQUEST(LlmRequestKind.INFERENCE_TURN, "taskagent/context-request", "request"),
    CONTEXT_ATTACHED_FILES(LlmRequestKind.INFERENCE_TURN, "taskagent/context-attached-files",
        "files"),
    CONTEXT_WORKSPACE_FILE(LlmRequestKind.INFERENCE_TURN, "taskagent/context-workspace-file",
        "filename", "contentType"),
    CONTEXT_OBJECT_STORAGE_FILE(LlmRequestKind.INFERENCE_TURN,
        "taskagent/context-object-storage-file", "filename", "id", "contentType"),
    CONTEXT_PLAN(LlmRequestKind.INFERENCE_TURN, "taskagent/context-plan", "plan"),
    CONTEXT_PARENT_TASK(LlmRequestKind.INFERENCE_TURN, "taskagent/context-parent-task",
        "title", "description", "partialResult"),
    CONTEXT_DEPENDENCIES(LlmRequestKind.INFERENCE_TURN, "taskagent/context-dependencies",
        "dependencies"),
    CONTEXT_DEPENDENCY(LlmRequestKind.INFERENCE_TURN, "taskagent/context-dependency",
        "title", "result"),
    CONTEXT_PREVIOUS_ATTEMPTS(LlmRequestKind.INFERENCE_TURN,
        "taskagent/context-previous-attempts", "attempts"),
    CONTEXT_PREVIOUS_ATTEMPT(LlmRequestKind.INFERENCE_TURN, "taskagent/context-previous-attempt",
        "attemptNumber", "verdict");

    private final LlmRequestKind kind;

    private final String name;

    private final Set<String> variables;

    PromptKey(final LlmRequestKind kind, final String name, final String... variables) {
        this.kind = kind;
        this.name = name;
        this.variables = Set.of(variables);
    }

    /**
     * The call kind this template shapes.
     *
     * @return the kind whose fingerprint covers this template
     */
    public LlmRequestKind kind() {
        return this.kind;
    }

    /**
     * Where the template lives, relative to the prompts root and without its extension.
     *
     * @return the template's name, e.g. {@code review/evaluation-system}
     */
    public String templateName() {
        return this.name;
    }

    /**
     * The variables the code fills this template with.
     *
     * @return the variable names, which every render supplies (possibly as {@code null})
     */
    public Set<String> variables() {
        return this.variables;
    }
}
