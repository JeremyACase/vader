package org.vader.core.server.review.model;

import java.util.List;
import org.vader.common.model.vader.entity.TaskAttemptStatus;
import org.vader.core.server.workflow.model.PlanStep;

/**
 * Everything {@code EvaluationLlmExecutor} needs to independently judge whether one
 * settled attempt actually succeeded, rather than trusting the harness's own self-report at face
 * value.
 *
 * @param userRequest the client prompt the task's workflow was planned from -- what the user
 *     actually asked for, so a task is judged against that rather than against whatever a
 *     literal reading of its own title implies
 * @param planObjective the objective of the plan the task belongs to
 * @param planSteps every task of that plan, in order, with the task under review marked -- so
 *     the task is judged on its own share of the request, not on work another task owns
 * @param taskTitle the task's title
 * @param taskDescription the task's description
 * @param attemptStatus the harness's own self-reported status ({@code SUCCEEDED} or
 *     {@code FAILED} -- this request is never built for any other status)
 * @param attemptResult the attempt's reported result, or {@code null}
 * @param attemptFailureReason the attempt's reported failure reason, or {@code null}
 * @param priorUpdateDescriptions every prior update recorded against this task, oldest first, for
 *     context on what earlier attempts already tried
 * @param lastToolCallEvidence a one-line summary of the attempt's last tool call and whether it
 *     errored, or {@code null} if it made none -- deterministic evidence the reported text alone
 *     can hide (an agent can end on a confident reply right after its code raised)
 */
public record EvaluationRequest(
    String userRequest,
    String planObjective,
    List<PlanStep> planSteps,
    String taskTitle,
    String taskDescription,
    TaskAttemptStatus attemptStatus,
    String attemptResult,
    String attemptFailureReason,
    List<String> priorUpdateDescriptions,
    String lastToolCallEvidence) {
}
