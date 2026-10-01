package org.vader.common.library.implementation.service.mapper;

import java.util.Objects;
import org.vader.common.model.vader.dto.AbstractOutboxMessage;
import org.vader.common.model.vader.entity.AbstractOutboxMessageEntity;

/**
 * Base class for queue-message mappers, adding the copy of the lifecycle fields every
 * inbox/outbox message carries.
 *
 * @param <F> the queue message entity type
 * @param <T> the queue message DTO type
 */
public abstract class AbstractOutboxMessageDtoMapper<
    F extends AbstractOutboxMessageEntity,
    T extends AbstractOutboxMessage>
    extends GenericDtoMapper<F, T> {

    /**
     * Copies the identity, audit and queue-lifecycle fields present on every queue message.
     *
     * @param from the entity to copy from
     * @param to the DTO to copy to
     */
    protected void setOutboxMessageFields(final F from, final T to) {
        if (Objects.nonNull(from) && Objects.nonNull(to)) {
            super.setAbstractModelFields(from, to);
            to.setStatus(from.getStatus());
            to.setClaimedAt(from.getClaimedAt());
            to.setProcessedAt(from.getProcessedAt());
            to.setAttempts(from.getAttempts());
            to.setFailureReason(from.getFailureReason());
        }
    }
}
