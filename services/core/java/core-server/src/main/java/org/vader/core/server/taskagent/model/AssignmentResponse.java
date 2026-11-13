package org.vader.core.server.taskagent.model;

/**
 * Wire shape returned from {@code GET /vader/core-server/agent/assignments/{assignmentId}}: the
 * work order a harness fetches once, at startup.
 *
 * @param taskId the task this harness is responsible for
 * @param assignmentId this dispatch's id (echoed back for convenience)
 * @param objective what the task is trying to accomplish
 * @param prompts every message the harness sends the model on its own initiative. The opening
 *     task message carries the background a task's own short description never carries on its
 *     own: the original client-submitted request, any files attached to it, and the results of
 *     any prerequisite tasks this one depends on -- a task otherwise has no way to discover the
 *     request, or its own dependencies' output, on its own.
 * @param maxTurns the turn cap this attempt must respect
 * @param maxTokens the token cap this attempt must respect
 * @param deadlineSeconds how long, from now, this attempt has to finish
 */
public record AssignmentResponse(
    String taskId,
    String assignmentId,
    String objective,
    AssignmentPrompts prompts,
    int maxTurns,
    long maxTokens,
    long deadlineSeconds) {
}
