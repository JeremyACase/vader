package org.vader.core.server.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.vader.common.model.vader.entity.TaskAttemptToolCallEntity;

/** Spring Data repository for {@link TaskAttemptToolCallEntity}. */
public interface TaskAttemptToolCallRepository
    extends JpaRepository<TaskAttemptToolCallEntity, String> {
}
