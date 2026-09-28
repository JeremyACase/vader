package org.vader.core.server.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.vader.common.model.vader.entity.TaskAttemptTranscriptEntity;

/** Spring Data repository for {@link TaskAttemptTranscriptEntity}. */
public interface TaskAttemptTranscriptRepository
    extends JpaRepository<TaskAttemptTranscriptEntity, String> {

    /**
     * Counts how many turns have already been logged for an attempt, used to assign the next
     * transcript row's {@code turnIndex}.
     *
     * @param taskAttemptId the attempt id
     * @return the number of turns already logged
     */
    long countByTaskAttemptId(String taskAttemptId);

    /**
     * Fetches the most recently logged turn for an attempt, used to find where the next turn's
     * incremental {@code prompt} delta should start slicing from.
     *
     * @param taskAttemptId the attempt id
     * @return the last-logged turn, or empty if none has been logged yet
     */
    Optional<TaskAttemptTranscriptEntity> findFirstByTaskAttemptIdOrderByTurnIndexDesc(
        String taskAttemptId);
}
