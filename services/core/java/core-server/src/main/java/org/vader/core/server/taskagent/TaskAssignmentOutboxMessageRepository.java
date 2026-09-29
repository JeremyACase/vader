package org.vader.core.server.taskagent;

import org.vader.common.model.vader.entity.TaskAssignmentOutboxMessageEntity;
import org.vader.core.server.messaging.OutboxMessageRepository;

/** Spring Data repository for {@link TaskAssignmentOutboxMessageEntity} queue messages. */
public interface TaskAssignmentOutboxMessageRepository
    extends OutboxMessageRepository<TaskAssignmentOutboxMessageEntity> {
}
