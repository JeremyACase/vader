package org.vader.core.server.models;

/**
 * Published after a workflow's task graph is persisted, so {@link TaskGraphScheduler} can
 * dispatch its initial ready set (every task with no unmet dependencies) without waiting for a
 * scheduled poll.
 *
 * @param workflowId the id of the newly-decomposed workflow
 */
public record WorkflowDecomposedEvent(String workflowId) {
}
