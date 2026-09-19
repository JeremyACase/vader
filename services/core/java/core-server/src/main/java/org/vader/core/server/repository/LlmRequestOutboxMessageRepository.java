package org.vader.core.server.repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.vader.common.model.vader.entity.LlmRequestOutboxMessageEntity;

/** Spring Data repository for {@link LlmRequestOutboxMessageEntity} queue messages. */
public interface LlmRequestOutboxMessageRepository
    extends OutboxMessageRepository<LlmRequestOutboxMessageEntity> {

    /**
     * The most recent time any message on this queue was claimed, across every replica -- used by
     * {@link org.vader.core.server.service.llm.LlmRequestQueue} to tell "the queue is deep but
     * still moving" apart from "nothing has happened in a while, the backend is likely stuck",
     * since {@code maxOpenMessages() == 1} means claims happen strictly one at a time and a claim
     * that never resolves leaves this timestamp frozen at when it was claimed.
     *
     * @return the most recent claim time, or empty if no message has ever been claimed
     */
    @Query("select max(m.claimedAt) from LlmRequestOutboxMessageEntity m")
    Optional<OffsetDateTime> findMostRecentClaimedAt();
}
