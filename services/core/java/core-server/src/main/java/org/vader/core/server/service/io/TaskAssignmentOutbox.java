package org.vader.core.server.service.io;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.vader.common.model.vader.entity.TaskAssignmentOutboxMessageEntity;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.core.server.repository.TaskAssignmentOutboxMessageRepository;

/**
 * Outbox for task assignments: writes a queue message for each newly-created
 * {@link TaskAttemptEntity} so {@link TaskAssignmentInbox} can dispatch it to the agent-harness
 * operator.
 */
@Service
public class TaskAssignmentOutbox
    extends AbstractOutbox<TaskAttemptEntity, TaskAssignmentOutboxMessageEntity> {

    @Autowired
    private TaskAssignmentOutboxMessageRepository messageRepository;

    @Override
    protected TaskAssignmentOutboxMessageEntity newMessage(final TaskAttemptEntity payload) {
        var message = new TaskAssignmentOutboxMessageEntity();
        message.setTaskAttempt(payload);
        return message;
    }

    @Override
    protected JpaRepository<TaskAssignmentOutboxMessageEntity, String> repository() {
        return this.messageRepository;
    }

    @Override
    protected String queuedModelType() {
        return "TaskAssignment";
    }
}
