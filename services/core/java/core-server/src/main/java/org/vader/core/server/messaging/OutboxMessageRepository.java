package org.vader.core.server.messaging;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;
import org.vader.common.model.vader.entity.AbstractOutboxMessageEntity;
import org.vader.common.model.vader.entity.OutboxMessageStatus;

/**
 * Common repository operations for any inbox/outbox queue message entity. Concrete repositories
 * bind the type parameter and become the Spring Data bean.
 *
 * @param <M> the queue message entity type
 */
@NoRepositoryBean
public interface OutboxMessageRepository<M extends AbstractOutboxMessageEntity>
    extends JpaRepository<M, String> {

    /**
     * Finds the oldest message in the given status, ordered by creation time.
     *
     * @param status the status to match
     * @return the oldest matching message, or empty if none
     */
    Optional<M> findFirstByStatusOrderByCreatedAtAsc(OutboxMessageStatus status);

    /**
     * Counts messages in the given status.
     *
     * @param status the status to match
     * @return the match count
     */
    long countByStatus(OutboxMessageStatus status);

    /**
     * Atomically claims one message: flips it from {@code pending} to {@code claimed} only if it
     * is still {@code pending}. The conditional update is atomic in the database, which is what
     * makes claiming safe across replicas; the loser gets {@code 0} and moves on.
     *
     * @param id the message id to attempt to claim
     * @param claimedAt the timestamp to stamp it with if this claim wins
     * @param pending the status a message must currently hold for this to succeed
     * @param claimed the status to flip it to
     * @return {@code 1} if this call won the claim, {@code 0} if another caller already had
     */
    @Modifying(clearAutomatically = true)
    @Query("update #{#entityName} m set m.status = :claimed, m.claimedAt = :claimedAt, "
        + "m.attempts = m.attempts + 1 where m.id = :id and m.status = :pending")
    int claimIfStillPending(
        @Param("id") String id,
        @Param("claimedAt") OffsetDateTime claimedAt,
        @Param("pending") OutboxMessageStatus pending,
        @Param("claimed") OutboxMessageStatus claimed);
}
