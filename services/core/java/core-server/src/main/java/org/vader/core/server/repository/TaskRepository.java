package org.vader.core.server.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.vader.common.model.vader.entity.TaskEntity;

/** Spring Data repository for {@link TaskEntity}. */
public interface TaskRepository extends JpaRepository<TaskEntity, String> {
}
