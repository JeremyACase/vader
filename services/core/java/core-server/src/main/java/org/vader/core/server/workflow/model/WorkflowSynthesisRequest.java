package org.vader.core.server.workflow.model;

import java.util.List;

/**
 * Everything {@code WorkflowSynthesisLlmExecutor} needs to write one final answer for a
 * completed workflow, rolling up every task's own result into a single coherent response instead
 * of a list of what each task did.
 *
 * @param promptText the original client prompt
 * @param objective the task plan's stated objective
 * @param taskOutcomes every task's contribution, in task-graph order
 * @param deliveredFiles every file the tasks uploaded for the user, possibly empty
 */
public record WorkflowSynthesisRequest(
    String promptText,
    String objective,
    List<TaskOutcome> taskOutcomes,
    List<DeliveredFile> deliveredFiles) {
}
