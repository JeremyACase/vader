package org.vader.common.library.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.vader.common.model.vader.entity.TaskEntity;

/** Seed/cleanup helper for the DAO slice tests. */
public interface TestTaskRepository extends JpaRepository<TaskEntity, String> {
}
