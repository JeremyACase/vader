package org.vader.common.model.vader.entity;

/**
 * Overall progress of a {@link WorkflowEntity}, derived from the terminal states of every
 * {@link TaskAttemptEntity} across its task graph.
 */
public enum WorkflowStatus {
    RUNNING,
    SUCCEEDED,
    FAILED
}
