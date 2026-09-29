package org.vader.core.server.messaging;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.vader.common.model.vader.entity.AbstractOutboxMessageEntity;
import org.vader.common.model.vader.entity.OutboxMessageStatus;
import org.vader.core.server.backpressure.InterfaceQueueBackpressure;

/**
 * Shared behaviour for an inbox: pop the oldest pending message and process it, repeating until
 * the queue is empty or the concurrency ceiling is reached.
 *
 * <p>Each message moves through three independent transactions -- claim, process, mark -- so a
 * processing failure only fails that one message. Claim and the terminal marks run via
 * {@link QueueMessageProcessor}; {@link #handle} runs between them, outside any of those
 * transactions.</p>
 *
 * <p>{@link #drain()} is guarded by a per-instance flag so overlapping drains on one replica
 * coalesce. Across replicas, the claim ({@link QueueMessageProcessor#claim}) is an atomic
 * conditional {@code UPDATE ... WHERE status = PENDING}, so any number can drain one table.</p>
 *
 * <p>Concrete subclasses supply the repository, {@link #handle}, and the
 * {@link InterfaceQueueBackpressure} identity/ceiling methods ({@code queuedModelType()},
 * {@code maxOpenMessages()}).</p>
 *
 * @param <M> the queue message entity type
 */
public abstract class AbstractInbox<M extends AbstractOutboxMessageEntity>
    implements InterfaceInbox<M>, InterfaceQueueBackpressure {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final AtomicBoolean draining = new AtomicBoolean(false);

    @Autowired
    private QueueMessageProcessor processor;

    @Override
    public Optional<M> pop() {
        return this.processor.claim(
            this.repository(),
            () -> this.repository().findFirstByStatusOrderByCreatedAtAsc(
                OutboxMessageStatus.PENDING));
    }

    @Override
    public void drain() {
        if (!this.draining.compareAndSet(false, true)) {
            this.logger.debug("{} drain already in progress; skipping", this.queuedModelType());
            return;
        }
        try {
            this.drainWhileCapacityRemains();
        } finally {
            this.draining.set(false);
        }
    }

    @Override
    public long queuedRecordCount() {
        return this.repository().countByStatus(OutboxMessageStatus.PENDING);
    }

    @Override
    public long openMessageCount() {
        return this.repository().countByStatus(OutboxMessageStatus.CLAIMED);
    }

    /**
     * Returns the repository for this queue's message entity.
     *
     * @return the message repository
     */
    protected abstract OutboxMessageRepository<M> repository();

    /**
     * Processes one claimed message. Runs in its own transaction (or none); a thrown exception
     * marks the message {@code FAILED}.
     *
     * @param message the claimed message
     */
    protected abstract void handle(M message);

    private void drainWhileCapacityRemains() {
        var processed = 0;
        while (this.openMessageCount() < this.maxOpenMessages()) {
            var claimed = this.pop();
            if (claimed.isEmpty()) {
                break;
            }
            this.processClaimed(claimed.get());
            processed++;
        }
        if (processed > 0) {
            this.logger.info("{} inbox drained {} message(s)", this.queuedModelType(), processed);
        }
    }

    /**
     * Settles a message whose {@link #handle} threw. By default, marks it {@code FAILED}; an inbox
     * whose failures can be transient overrides this to put the message back on its queue
     * instead.
     *
     * @param message the claimed message
     * @param failure what {@link #handle} threw
     */
    protected void onHandleFailure(final M message, final RuntimeException failure) {
        this.processor.markFailed(this.repository(), message.getId(), failure.toString());
    }

    /**
     * Returns the processor that runs this inbox's claim and mark transactions.
     *
     * @return the queue message processor
     */
    protected QueueMessageProcessor processor() {
        return this.processor;
    }

    private void processClaimed(final M message) {
        try {
            this.handle(message);
            this.processor.markProcessed(this.repository(), message.getId());
        } catch (RuntimeException e) {
            this.onHandleFailure(message, e);
        }
    }
}
