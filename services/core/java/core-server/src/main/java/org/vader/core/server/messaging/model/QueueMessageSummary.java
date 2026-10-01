package org.vader.core.server.messaging.model;

import java.time.OffsetDateTime;
import org.vader.common.model.vader.entity.OutboxMessageStatus;

/**
 * One queue message as a list row: its lifecycle without its payload, which a queue such as the
 * LLM request queue makes too large to send a page at a time.
 *
 * @param id the message id
 * @param modelType the message's DTO model type
 * @param status where the message is in its lifecycle
 * @param createdAt when the outbox wrote it
 * @param claimedAt when an inbox last claimed it, if ever
 * @param processedAt when it was processed, if it has been
 * @param attempts how many times it has been claimed
 * @param subject a short description of what it carries: a request kind or a payload id
 */
public record QueueMessageSummary(
    String id,
    String modelType,
    OutboxMessageStatus status,
    OffsetDateTime createdAt,
    OffsetDateTime claimedAt,
    OffsetDateTime processedAt,
    int attempts,
    String subject) {
}
