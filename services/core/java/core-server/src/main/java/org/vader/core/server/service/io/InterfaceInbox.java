package org.vader.core.server.service.io;

import java.util.Optional;
import org.vader.common.model.vader.entity.AbstractOutboxMessageEntity;

/**
 * Read side of an inbox/outbox queue: claims pending messages one at a time and processes them.
 *
 * @param <M> the queue message entity type
 */
public interface InterfaceInbox<M extends AbstractOutboxMessageEntity> {

    /**
     * Claims the oldest pending message, marking it {@code CLAIMED}.
     *
     * @return the claimed message, or empty if the queue holds nothing pending
     */
    Optional<M> pop();

    /**
     * Claims and processes pending messages until the queue is empty or the concurrency ceiling
     * is reached. Safe to call concurrently -- overlapping calls are coalesced.
     */
    void drain();
}
