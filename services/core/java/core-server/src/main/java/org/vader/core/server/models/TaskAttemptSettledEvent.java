package org.vader.core.server.models;

/**
 * Published whenever a {@code TaskAttempt} reaches a terminal status ({@code SUCCEEDED},
 * {@code FAILED}, {@code TIMED_OUT}, or {@code STALLED}) -- whether because the harness reported
 * it, or because dispatch itself failed. {@link TaskGraphScheduler} reacts by re-evaluating the
 * owning workflow: retrying the task, dispatching newly-unblocked dependents, or closing out the
 * workflow if every task has now settled.
 *
 * @param workflowId the id of the workflow the settled task belongs to
 */
public record TaskAttemptSettledEvent(String workflowId) {
}
