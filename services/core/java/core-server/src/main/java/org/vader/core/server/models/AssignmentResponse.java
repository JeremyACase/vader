package org.vader.core.server.models;

/**
 * Wire shape returned from {@code GET /vader/core-server/agent/assignments/{assignmentId}}: the
 * work order a harness fetches once, at startup.
 *
 * @param taskId the task this harness is responsible for
 * @param assignmentId this dispatch's id (echoed back for convenience)
 * @param objective what the task is trying to accomplish
 * @param context background a task's own short description never carries on its own: the
 *     original client-submitted request, any files attached to it, and the results of any
 *     prerequisite tasks this one depends on. Always at least the original request -- a task
 *     otherwise has no way to discover it, or its own dependencies' output, on its own.
 * @param maxTurns the turn cap this attempt must respect
 * @param maxTokens the token cap this attempt must respect
 * @param deadlineSeconds how long, from now, this attempt has to finish
 */
public record AssignmentResponse(
    String taskId,
    String assignmentId,
    String objective,
    String context,
    int maxTurns,
    long maxTokens,
    long deadlineSeconds) {
}
