package org.vader.core.server.taskagent;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.vader.common.model.vader.entity.TaskAttemptToolCallEntity;

/** Spring Data repository for {@link TaskAttemptToolCallEntity}. */
public interface TaskAttemptToolCallRepository
    extends JpaRepository<TaskAttemptToolCallEntity, String> {

    /**
     * The most recent tool call one attempt made.
     *
     * @param taskAttemptId the attempt's id
     * @return its latest tool call, or empty if it made none
     */
    Optional<TaskAttemptToolCallEntity> findFirstByTaskAttemptIdOrderByCreatedAtDesc(
        String taskAttemptId);
}
