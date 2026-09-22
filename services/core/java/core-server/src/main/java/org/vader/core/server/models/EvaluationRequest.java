package org.vader.core.server.models;

import java.util.List;
import org.vader.common.model.vader.entity.TaskAttemptStatus;

/**
 * Everything an {@code InterfaceEvaluatorStrategy} needs to independently judge whether one
 * settled attempt actually succeeded, rather than trusting the harness's own self-report at face
 * value.
 *
 * @param taskTitle the task's title
 * @param taskDescription the task's description
 * @param attemptStatus the harness's own self-reported status ({@code SUCCEEDED} or
 *     {@code FAILED} -- this request is never built for any other status)
 * @param attemptResult the attempt's reported result, or {@code null}
 * @param attemptFailureReason the attempt's reported failure reason, or {@code null}
 * @param priorUpdateDescriptions every prior update recorded against this task, oldest first, for
 *     context on what earlier attempts already tried
 */
public record EvaluationRequest(
    String taskTitle,
    String taskDescription,
    TaskAttemptStatus attemptStatus,
    String attemptResult,
    String attemptFailureReason,
    List<String> priorUpdateDescriptions) {
}
