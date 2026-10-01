package org.vader.core.server.messaging.model;

import java.util.List;

/**
 * One page of a queue's messages, newest first.
 *
 * @param content the messages on this page
 * @param page the zero-based page number
 * @param size the requested page size
 * @param totalElements how many messages match across every page
 * @param totalPages how many pages there are
 */
public record QueueMessagePage(
    List<QueueMessageSummary> content,
    int page,
    int size,
    long totalElements,
    int totalPages) {
}
