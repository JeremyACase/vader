package org.vader.core.server.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;
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
}
