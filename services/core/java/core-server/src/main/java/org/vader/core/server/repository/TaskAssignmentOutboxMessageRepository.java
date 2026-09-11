package org.vader.core.server.repository;

import org.vader.common.model.vader.entity.TaskAssignmentOutboxMessageEntity;

/** Spring Data repository for {@link TaskAssignmentOutboxMessageEntity} queue messages. */
public interface TaskAssignmentOutboxMessageRepository
    extends OutboxMessageRepository<TaskAssignmentOutboxMessageEntity> {
}
