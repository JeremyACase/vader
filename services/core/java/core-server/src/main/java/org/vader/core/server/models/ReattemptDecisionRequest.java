package org.vader.core.server.models;

import java.util.List;

/**
 * Everything an {@code InterfaceReattemptDecisionStrategy} needs to judge whether a failed task
 * is worth re-attempting. Built only once the attempt cap has already been checked -- this
 * request is never even assembled once no budget remains.
 *
 * @param taskTitle the task's title
 * @param taskDescription the task's description
 * @param attemptNumber the failed attempt's number
 * @param maxAttempts the configured cap on attempts per task
 * @param latestFailureReasoning the evaluator's (or the deterministic timeout/stall) reasoning for
 *     why the latest attempt failed
 * @param priorUpdateDescriptions every prior update recorded against this task, oldest first
 */
public record ReattemptDecisionRequest(
    String taskTitle,
    String taskDescription,
    int attemptNumber,
    int maxAttempts,
    String latestFailureReasoning,
    List<String> priorUpdateDescriptions) {
}
