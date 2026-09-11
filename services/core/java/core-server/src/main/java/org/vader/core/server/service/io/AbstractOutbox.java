package org.vader.core.server.service.io;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.library.implementation.service.builder.VaderIngressResponseBuilder;
import org.vader.common.model.vader.IngressResponse;
import org.vader.common.model.vader.entity.AbstractModelEntity;
import org.vader.common.model.vader.entity.AbstractOutboxMessageEntity;
import org.vader.common.model.vader.entity.OutboxMessageStatus;

/**
 * Shared behaviour for an outbox: build a {@code PENDING} message for a payload, persist it in a
 * single transaction, announce it, and hand the caller an {@link IngressResponse} receipt.
 *
 * <p>Concrete subclasses supply the message construction, the repository, and the payload model
 * type.</p>
 *
 * @param <P> the payload entity type
 * @param <M> the queue message entity type
 */
public abstract class AbstractOutbox<
    P extends AbstractModelEntity,
    M extends AbstractOutboxMessageEntity>
    implements InterfaceOutbox<P> {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private VaderIngressResponseBuilder ingressResponseBuilder;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public IngressResponse enqueue(final P payload) {
        var message = this.newMessage(payload);
        message.setStatus(OutboxMessageStatus.PENDING);
        this.repository().save(message);
        this.logger.info("Enqueued {} outbox message {} for payload {}",
            this.queuedModelType(), message.getId(), payload.getId());
        this.eventPublisher.publishEvent(new OutboxMessageEnqueuedEvent(this.queuedModelType()));
        return this.ingressResponseBuilder.buildIngressResponseFrom(payload);
    }

    /**
     * Builds a transient queue message wrapping the given payload.
     *
     * @param payload the persisted payload entity
     * @return a new, unsaved message
     */
    protected abstract M newMessage(P payload);

    /**
     * Returns the Spring Data repository for this queue's message entity.
     *
     * @return the message repository
     */
    protected abstract JpaRepository<M, String> repository();

    /**
     * Returns the {@code modelType} of the payload this outbox queues, e.g.
     * {@code "ClientPrompt"}.
     *
     * @return the queued payload model type
     */
    protected abstract String queuedModelType();
}
