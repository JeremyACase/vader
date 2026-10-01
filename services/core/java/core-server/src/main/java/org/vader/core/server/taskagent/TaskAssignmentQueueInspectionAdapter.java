package org.vader.core.server.taskagent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.vader.common.library.implementation.interfaces.mapper.InterfaceEntityToDtoMapper;
import org.vader.common.library.implementation.service.mapper.TaskAssignmentOutboxMessageDtoMapper;
import org.vader.common.model.vader.dto.TaskAssignmentOutboxMessage;
import org.vader.common.model.vader.entity.TaskAssignmentOutboxMessageEntity;
import org.vader.core.server.messaging.AbstractInbox;
import org.vader.core.server.messaging.AbstractQueueInspectionAdapter;
import org.vader.core.server.messaging.OutboxMessageRepository;

/** Inspection of the harness dispatch queue; a message's subject is its task attempt id. */
@Service
public class TaskAssignmentQueueInspectionAdapter extends
    AbstractQueueInspectionAdapter<TaskAssignmentOutboxMessageEntity, TaskAssignmentOutboxMessage> {

    @Autowired
    private TaskAssignmentInbox inbox;

    @Autowired
    private TaskAssignmentOutboxMessageRepository repository;

    @Autowired
    private TaskAssignmentOutboxMessageDtoMapper mapper;

    @Override
    protected AbstractInbox<TaskAssignmentOutboxMessageEntity> inbox() {
        return this.inbox;
    }

    @Override
    protected OutboxMessageRepository<TaskAssignmentOutboxMessageEntity> repository() {
        return this.repository;
    }

    @Override
    protected InterfaceEntityToDtoMapper<
        TaskAssignmentOutboxMessageEntity, TaskAssignmentOutboxMessage> mapper() {
        return this.mapper;
    }

    @Override
    protected String subjectOf(final TaskAssignmentOutboxMessage message) {
        return message.getTaskAttemptId();
    }
}
