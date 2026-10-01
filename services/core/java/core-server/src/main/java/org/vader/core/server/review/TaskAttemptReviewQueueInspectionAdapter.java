package org.vader.core.server.review;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.vader.common.library.implementation.interfaces.mapper.InterfaceEntityToDtoMapper;
import org.vader.common.library.implementation.service.mapper.TaskAttemptReviewOutboxMessageDtoMapper;
import org.vader.common.model.vader.dto.TaskAttemptReviewOutboxMessage;
import org.vader.common.model.vader.entity.TaskAttemptReviewOutboxMessageEntity;
import org.vader.core.server.messaging.AbstractInbox;
import org.vader.core.server.messaging.AbstractQueueInspectionAdapter;
import org.vader.core.server.messaging.OutboxMessageRepository;

/** Inspection of the attempt review queue; a message's subject is its task attempt id. */
@Service
public class TaskAttemptReviewQueueInspectionAdapter extends AbstractQueueInspectionAdapter<
    TaskAttemptReviewOutboxMessageEntity, TaskAttemptReviewOutboxMessage> {

    @Autowired
    private TaskAttemptReviewInbox inbox;

    @Autowired
    private TaskAttemptReviewOutboxMessageRepository repository;

    @Autowired
    private TaskAttemptReviewOutboxMessageDtoMapper mapper;

    @Override
    protected AbstractInbox<TaskAttemptReviewOutboxMessageEntity> inbox() {
        return this.inbox;
    }

    @Override
    protected OutboxMessageRepository<TaskAttemptReviewOutboxMessageEntity> repository() {
        return this.repository;
    }

    @Override
    protected InterfaceEntityToDtoMapper<
        TaskAttemptReviewOutboxMessageEntity, TaskAttemptReviewOutboxMessage> mapper() {
        return this.mapper;
    }

    @Override
    protected String subjectOf(final TaskAttemptReviewOutboxMessage message) {
        return message.getTaskAttemptId();
    }
}
