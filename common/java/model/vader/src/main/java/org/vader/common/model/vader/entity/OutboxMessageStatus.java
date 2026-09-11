package org.vader.common.model.vader.entity;

/**
 * Lifecycle of a message sitting on an inbox/outbox queue.
 *
 * <ul>
 *   <li>{@code PENDING} -- written by the outbox, not yet claimed.</li>
 *   <li>{@code CLAIMED} -- popped by the inbox and currently being processed.</li>
 *   <li>{@code PROCESSED} -- processing completed successfully.</li>
 *   <li>{@code FAILED} -- processing threw; see {@code failureReason}.</li>
 * </ul>
 */
public enum OutboxMessageStatus {
    PENDING,
    CLAIMED,
    PROCESSED,
    FAILED
}
