package org.vader.common.model.vader.entity;

/**
 * Lifecycle of one {@link TaskAttemptEntity}: a single dispatch of a {@link TaskEntity} to an
 * agent harness.
 *
 * <ul>
 *   <li>{@code PENDING} -- created by the scheduler, not yet dispatched to an operator.</li>
 *   <li>{@code DISPATCHED} -- a harness Job was created; it has not yet made contact.</li>
 *   <li>{@code RUNNING} -- the harness fetched this assignment and is actively working it.</li>
 *   <li>{@code SUCCEEDED} -- the harness completed the task and reported a result.</li>
 *   <li>{@code FAILED} -- the harness reported a failure, or dispatch itself failed.</li>
 *   <li>{@code TIMED_OUT} -- the harness's own deadline elapsed before it finished.</li>
 *   <li>{@code STALLED} -- the harness detected it was repeating the same action with no
 *       progress and gave up rather than exhausting its full budget.</li>
 * </ul>
 */
public enum TaskAttemptStatus {
    PENDING,
    DISPATCHED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    TIMED_OUT,
    STALLED
}
