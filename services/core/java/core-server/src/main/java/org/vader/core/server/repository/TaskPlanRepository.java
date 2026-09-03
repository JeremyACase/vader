package org.vader.core.server.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.vader.common.model.vader.entity.TaskPlanEntity;

/** Spring Data repository for {@link TaskPlanEntity}. */
public interface TaskPlanRepository extends JpaRepository<TaskPlanEntity, String> {
}
