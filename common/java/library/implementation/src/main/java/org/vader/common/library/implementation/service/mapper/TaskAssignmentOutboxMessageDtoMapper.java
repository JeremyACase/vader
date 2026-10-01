package org.vader.common.library.implementation.service.mapper;

import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.model.vader.dto.TaskAssignmentOutboxMessage;
import org.vader.common.model.vader.entity.TaskAssignmentOutboxMessageEntity;

/**
 * Maps {@link TaskAssignmentOutboxMessageEntity} to {@link TaskAssignmentOutboxMessage} DTOs,
 * emitting the carried attempt as a shallow id reference.
 */
@Service
@Transactional
public class TaskAssignmentOutboxMessageDtoMapper extends
    AbstractOutboxMessageDtoMapper<TaskAssignmentOutboxMessageEntity, TaskAssignmentOutboxMessage> {

    @Override
    public TaskAssignmentOutboxMessage map(final TaskAssignmentOutboxMessageEntity from) {
        TaskAssignmentOutboxMessage to = null;
        if (Objects.nonNull(from)) {
            to = new TaskAssignmentOutboxMessage();
            super.setOutboxMessageFields(from, to);
            if (Objects.nonNull(from.getTaskAttempt())) {
                to.setTaskAttemptId(from.getTaskAttempt().getId());
            }
        }
        return to;
    }
}
