package org.vader.core.server.messaging.model;

import java.util.Map;
import org.vader.common.model.vader.entity.OutboxMessageStatus;

/**
 * One inbox/outbox queue at a glance: how many of its messages sit in each lifecycle status, and
 * how many it will process at once.
 *
 * @param name the queued payload model type identifying the queue, e.g. {@code "ClientPrompt"}
 * @param maxOpenMessages the most messages this queue processes concurrently
 * @param statusCounts message count per status, every status present
 */
public record QueueSummary(
    String name,
    int maxOpenMessages,
    Map<OutboxMessageStatus, Long> statusCounts) {
}
