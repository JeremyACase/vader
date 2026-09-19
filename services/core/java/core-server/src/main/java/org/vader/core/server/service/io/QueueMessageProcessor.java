package org.vader.core.server.service.io;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.model.vader.entity.AbstractOutboxMessageEntity;
import org.vader.common.model.vader.entity.OutboxMessageStatus;
import org.vader.core.server.repository.OutboxMessageRepository;

/**
 * Runs the status transitions of an inbox pop, each in its own transaction: the <em>claim</em>
 * ({@code PENDING} to {@code CLAIMED}) and the terminal <em>mark</em>s ({@code CLAIMED} to
 * {@code PROCESSED} or {@code FAILED}).
 *
 * <p>These live on a distinct bean -- rather than as self-invoked methods on {@code AbstractInbox}
 * -- so each transition commits independently: one failing message does not roll back the claim
 * of the next, and the actual processing runs outside any of these transactions so a processing
 * failure cannot poison the transaction that records it.</p>
 */
@Service
public class QueueMessageProcessor {

    private static final Logger logger = LoggerFactory.getLogger(QueueMessageProcessor.class);

    /**
     * Claims the oldest pending message the finder returns, in its own transaction -- safely
     * across multiple replicas polling the same table, via
     * {@link OutboxMessageRepository#claimIfStillPending}. If another replica's claim of the same
     * candidate wins the race, this retries against whatever the finder now reports as oldest
     * pending, rather than giving up.
     *
     * @param repository the message repository
     * @param pendingFinder supplies the oldest pending message, if any
     * @param <M> the message entity type
     * @return the claimed message, or empty if nothing was pending
     */
    @Transactional
    public <M extends AbstractOutboxMessageEntity> Optional<M> claim(
        final OutboxMessageRepository<M> repository,
        final Supplier<Optional<M>> pendingFinder) {

        var candidate = pendingFinder.get();
        while (candidate.isPresent()) {
            var id = candidate.get().getId();
            var won = repository.claimIfStillPending(
                id, OffsetDateTime.now(), OutboxMessageStatus.PENDING, OutboxMessageStatus.CLAIMED);
            if (won == 1) {
                var claimed = repository.findById(id);
                claimed.ifPresent(message -> logger.debug(
                    "Claimed queue message {} (attempt {})", message.getId(),
                    message.getAttempts()));
                return claimed;
            }
            logger.debug(
                "Lost the race to claim queue message {}; trying the next pending one", id);
            candidate = pendingFinder.get();
        }
        return Optional.empty();
    }

    /**
     * Marks a claimed message {@code PROCESSED}, in its own transaction.
     *
     * @param repository the message repository
     * @param messageId the message id
     * @param <M> the message entity type
     */
    @Transactional
    public <M extends AbstractOutboxMessageEntity> void markProcessed(
        final OutboxMessageRepository<M> repository,
        final String messageId) {

        var message = repository.findById(messageId).orElseThrow();
        message.setStatus(OutboxMessageStatus.PROCESSED);
        message.setProcessedAt(OffsetDateTime.now());
        repository.save(message);
        logger.debug("Processed queue message {}", messageId);
    }

    /**
     * Marks a claimed message {@code FAILED} with the given reason, in its own transaction.
     *
     * @param repository the message repository
     * @param messageId the message id
     * @param reason the failure detail
     * @param <M> the message entity type
     */
    @Transactional
    public <M extends AbstractOutboxMessageEntity> void markFailed(
        final OutboxMessageRepository<M> repository,
        final String messageId,
        final String reason) {

        var message = repository.findById(messageId).orElseThrow();
        message.setStatus(OutboxMessageStatus.FAILED);
        message.setFailureReason(reason);
        repository.save(message);
        logger.warn("Queue message {} failed: {}", messageId, reason);
    }
}
