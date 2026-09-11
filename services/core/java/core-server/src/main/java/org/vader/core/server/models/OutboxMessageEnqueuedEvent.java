package org.vader.core.server.models;

/**
 * Published after an outbox message is committed, so a listening inbox can drain its queue
 * immediately instead of waiting for the next scheduled poll.
 *
 * @param modelType the {@code modelType} of the enqueued payload (e.g. {@code "ClientPrompt"})
 */
public record OutboxMessageEnqueuedEvent(String modelType) {
}
