package org.vader.core.server.repository;

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
}
