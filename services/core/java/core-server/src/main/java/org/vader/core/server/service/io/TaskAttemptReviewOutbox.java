package org.vader.core.server.service.io;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskAttemptReviewOutboxMessageEntity;
import org.vader.core.server.repository.TaskAttemptReviewOutboxMessageRepository;

/**
 * Outbox for attempt review: writes a queue message for each newly-terminal
 * {@link TaskAttemptEntity} with no verdict yet, so {@link TaskAttemptReviewInbox} can evaluate it
 * (and, on failure, ask the orchestrator whether it's worth re-attempting).
 */
@Service
public class TaskAttemptReviewOutbox
    extends AbstractOutbox<TaskAttemptEntity, TaskAttemptReviewOutboxMessageEntity> {

    @Autowired
    private TaskAttemptReviewOutboxMessageRepository messageRepository;

    @Override
    protected TaskAttemptReviewOutboxMessageEntity newMessage(final TaskAttemptEntity payload) {
        var message = new TaskAttemptReviewOutboxMessageEntity();
        message.setTaskAttempt(payload);
        return message;
    }

    @Override
    protected JpaRepository<TaskAttemptReviewOutboxMessageEntity, String> repository() {
        return this.messageRepository;
    }

    @Override
    protected String queuedModelType() {
        return "TaskAttemptReview";
    }
}
