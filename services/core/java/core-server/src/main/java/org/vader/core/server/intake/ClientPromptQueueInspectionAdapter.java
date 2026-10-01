package org.vader.core.server.intake;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.vader.common.library.implementation.interfaces.mapper.InterfaceEntityToDtoMapper;
import org.vader.common.library.implementation.service.mapper.ClientPromptOutboxMessageDtoMapper;
import org.vader.common.model.vader.dto.ClientPromptOutboxMessage;
import org.vader.common.model.vader.entity.ClientPromptOutboxMessageEntity;
import org.vader.core.server.messaging.AbstractInbox;
import org.vader.core.server.messaging.AbstractQueueInspectionAdapter;
import org.vader.core.server.messaging.OutboxMessageRepository;

/** Inspection of the client-prompt decomposition queue; a message's subject is its prompt id. */
@Service
public class ClientPromptQueueInspectionAdapter extends
    AbstractQueueInspectionAdapter<ClientPromptOutboxMessageEntity, ClientPromptOutboxMessage> {

    @Autowired
    private ClientPromptInbox inbox;

    @Autowired
    private ClientPromptOutboxMessageRepository repository;

    @Autowired
    private ClientPromptOutboxMessageDtoMapper mapper;

    @Override
    protected AbstractInbox<ClientPromptOutboxMessageEntity> inbox() {
        return this.inbox;
    }

    @Override
    protected OutboxMessageRepository<ClientPromptOutboxMessageEntity> repository() {
        return this.repository;
    }

    @Override
    protected InterfaceEntityToDtoMapper<ClientPromptOutboxMessageEntity, ClientPromptOutboxMessage>
        mapper() {
        return this.mapper;
    }

    @Override
    protected String subjectOf(final ClientPromptOutboxMessage message) {
        return message.getClientPromptId();
    }
}
