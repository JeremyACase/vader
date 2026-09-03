package org.vader.core.server.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.vader.common.model.vader.entity.WorkflowEntity;

/** Spring Data repository for {@link WorkflowEntity}. */
public interface WorkflowRepository extends JpaRepository<WorkflowEntity, String> {
}
