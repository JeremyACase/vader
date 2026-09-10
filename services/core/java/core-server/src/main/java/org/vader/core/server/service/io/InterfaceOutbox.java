package org.vader.core.server.service.io;

import org.vader.common.model.vader.IngressResponse;
import org.vader.common.model.vader.entity.AbstractModelEntity;

/**
 * Write side of an inbox/outbox queue: accepts an already-persisted payload entity and enqueues
 * a message for a downstream {@link InterfaceInbox} to pop.
 *
 * @param <P> the payload entity type carried by the queue's messages
 */
public interface InterfaceOutbox<P extends AbstractModelEntity> {

    /**
     * Enqueues a queue message for the given payload and returns a receipt for the payload.
     *
     * @param payload the persisted payload entity to enqueue
     * @return an ingress response identifying the payload
     */
    IngressResponse enqueue(P payload);
}
