package org.vader.common.library.implementation.service.mapper;

import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.model.vader.dto.TaskAttemptReviewOutboxMessage;
import org.vader.common.model.vader.entity.TaskAttemptReviewOutboxMessageEntity;

/**
 * Maps {@link TaskAttemptReviewOutboxMessageEntity} to {@link TaskAttemptReviewOutboxMessage}
 * DTOs, emitting the carried attempt as a shallow id reference.
 */
@Service
@Transactional
public class TaskAttemptReviewOutboxMessageDtoMapper extends AbstractOutboxMessageDtoMapper<
    TaskAttemptReviewOutboxMessageEntity, TaskAttemptReviewOutboxMessage> {

    @Override
    public TaskAttemptReviewOutboxMessage map(final TaskAttemptReviewOutboxMessageEntity from) {
        TaskAttemptReviewOutboxMessage to = null;
        if (Objects.nonNull(from)) {
            to = new TaskAttemptReviewOutboxMessage();
            super.setOutboxMessageFields(from, to);
            if (Objects.nonNull(from.getTaskAttempt())) {
                to.setTaskAttemptId(from.getTaskAttempt().getId());
            }
            to.setNextAttemptAt(from.getNextAttemptAt());
        }
        return to;
    }
}
