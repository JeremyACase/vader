package org.vader.core.server.messaging;

import java.util.EnumMap;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.library.dao.service.PageValidator;
import org.vader.common.library.implementation.interfaces.mapper.InterfaceEntityToDtoMapper;
import org.vader.common.model.vader.dto.AbstractOutboxMessage;
import org.vader.common.model.vader.entity.AbstractOutboxMessageEntity;
import org.vader.common.model.vader.entity.OutboxMessageStatus;
import org.vader.core.server.messaging.model.QueueMessagePage;
import org.vader.core.server.messaging.model.QueueMessageSummary;
import org.vader.core.server.messaging.model.QueueSummary;

/**
 * Adapts one inbox/outbox queue to read-only inspection: its status counts, a page of its
 * messages, and any one message in full. Every read runs in a read-only transaction; nothing here
 * changes a message.
 *
 * <p>Concrete subclasses live with the queue they adapt and supply its inbox, repository and
 * mapper, plus a short {@link #subjectOf subject} for list rows.</p>
 *
 * @param <M> the queue message entity type
 * @param <D> the queue message DTO type
 */
public abstract class AbstractQueueInspectionAdapter<
    M extends AbstractOutboxMessageEntity,
    D extends AbstractOutboxMessage> {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

    @Autowired
    private PageValidator pageValidator;

    /**
     * Returns the name identifying this queue: its inbox's queued payload model type.
     *
     * @return the queue name
     */
    public String queueName() {
        return this.inbox().queuedModelType();
    }

    /**
     * Counts this queue's messages in each status.
     *
     * @return the queue summary
     */
    @Transactional(readOnly = true)
    public QueueSummary summary() {
        var counts = new EnumMap<OutboxMessageStatus, Long>(OutboxMessageStatus.class);
        for (var status : OutboxMessageStatus.values()) {
            counts.put(status, this.repository().countByStatus(status));
        }
        return new QueueSummary(this.queueName(), this.inbox().maxOpenMessages(), counts);
    }

    /**
     * Returns one page of this queue's messages, newest first, optionally only those in one
     * status.
     *
     * @param status the status to match, or {@code null} for every status
     * @param page the zero-based page number
     * @param size the page size
     * @return the page
     * @throws IllegalArgumentException if the page or size is out of range
     */
    @Transactional(readOnly = true)
    public QueueMessagePage messages(
        final OutboxMessageStatus status, final int page, final int size) {

        this.pageValidator.validatePageAndSize(page, size);
        var pageable = PageRequest.of(page, size, NEWEST_FIRST);
        var records = Optional.ofNullable(status)
            .map(matching -> this.repository().findByStatus(matching, pageable))
            .orElseGet(() -> this.repository().findAll(pageable));
        var content = this.mapper().map(records.getContent()).stream()
            .map(this::summaryOf)
            .toList();
        return new QueueMessagePage(
            content, records.getNumber(), records.getSize(), records.getTotalElements(),
            records.getTotalPages());
    }

    /**
     * Returns one message in full, payload included.
     *
     * @param id the message id
     * @return the message, or empty if this queue has none with that id
     */
    @Transactional(readOnly = true)
    public Optional<D> message(final String id) {
        return this.repository().findById(id).map(this.mapper()::map);
    }

    /**
     * Returns the inbox draining this queue.
     *
     * @return the inbox
     */
    protected abstract AbstractInbox<M> inbox();

    /**
     * Returns the repository for this queue's message entity.
     *
     * @return the message repository
     */
    protected abstract OutboxMessageRepository<M> repository();

    /**
     * Returns the mapper from this queue's message entity to its DTO.
     *
     * @return the mapper
     */
    protected abstract InterfaceEntityToDtoMapper<M, D> mapper();

    /**
     * Describes in a few words what one message carries, for its list row.
     *
     * @param message the mapped message
     * @return the subject
     */
    protected abstract String subjectOf(D message);

    private QueueMessageSummary summaryOf(final D message) {
        return new QueueMessageSummary(
            message.getId(), message.getModelType(), message.getStatus(), message.getCreatedAt(),
            message.getClaimedAt(), message.getProcessedAt(), message.getAttempts(),
            this.subjectOf(message));
    }
}
