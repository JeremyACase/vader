package org.vader.core.server.llm;

import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.vader.common.library.implementation.interfaces.mapper.InterfaceEntityToDtoMapper;
import org.vader.common.library.implementation.service.mapper.LlmRequestOutboxMessageDtoMapper;
import org.vader.common.model.vader.dto.LlmRequestOutboxMessage;
import org.vader.common.model.vader.entity.LlmRequestOutboxMessageEntity;
import org.vader.core.server.messaging.AbstractInbox;
import org.vader.core.server.messaging.AbstractQueueInspectionAdapter;
import org.vader.core.server.messaging.OutboxMessageRepository;

/** Inspection of the LLM request queue; a message's subject is its request kind. */
@Service
public class LlmRequestQueueInspectionAdapter
    extends AbstractQueueInspectionAdapter<LlmRequestOutboxMessageEntity, LlmRequestOutboxMessage> {

    @Autowired
    private LlmRequestInbox inbox;

    @Autowired
    private LlmRequestOutboxMessageRepository repository;

    @Autowired
    private LlmRequestOutboxMessageDtoMapper mapper;

    @Override
    protected AbstractInbox<LlmRequestOutboxMessageEntity> inbox() {
        return this.inbox;
    }

    @Override
    protected OutboxMessageRepository<LlmRequestOutboxMessageEntity> repository() {
        return this.repository;
    }

    @Override
    protected InterfaceEntityToDtoMapper<LlmRequestOutboxMessageEntity, LlmRequestOutboxMessage>
        mapper() {
        return this.mapper;
    }

    @Override
    protected String subjectOf(final LlmRequestOutboxMessage message) {
        return Objects.toString(message.getKind(), null);
    }
}
