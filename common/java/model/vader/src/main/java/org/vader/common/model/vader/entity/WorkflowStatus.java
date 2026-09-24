package org.vader.common.model.vader.entity;

/**
 * Overall progress of a {@link WorkflowEntity}, derived from the terminal states of every
 * {@link TaskAttemptEntity} across its task graph.
 */
public enum WorkflowStatus {
    /** In progress. */
    RUNNING,

    /**
     * In progress, but paused: a step that needs the LLM (reviewing a finished attempt) could not
     * reach it, and is being retried until it can. Not terminal -- the workflow returns to
     * {@link #RUNNING} on its own once the LLM answers again. Reported instead of a fabricated
     * verdict (the LLM was never consulted) and instead of hanging silently in {@link #RUNNING}.
     */
    AWAITING_LLM,

    /** Terminal: every task succeeded. */
    SUCCEEDED,

    /** Terminal: at least one task permanently failed. */
    FAILED
}
