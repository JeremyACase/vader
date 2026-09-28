package org.vader.core.server.models;

/**
 * Published whenever a {@code TaskAttempt} reaches a terminal status ({@code SUCCEEDED},
 * {@code FAILED}, {@code TIMED_OUT}, or {@code STALLED}) -- whether because the harness reported
 * it, or because dispatch itself failed. Two listeners react independently: {@link
 * TaskGraphScheduler} re-evaluates the owning workflow (retrying the task, dispatching
 * newly-unblocked dependents, or closing out the workflow if every task has now settled), and a
 * separate listener deletes the settled attempt's agent-harness Job.
 *
 * @param workflowId the id of the workflow the settled task belongs to
 * @param assignmentId the id of the attempt that settled -- also its agent-harness Job's identity
 *     (see {@code AgentHarnessNaming})
 */
public record TaskAttemptSettledEvent(String workflowId, String assignmentId) {
}
