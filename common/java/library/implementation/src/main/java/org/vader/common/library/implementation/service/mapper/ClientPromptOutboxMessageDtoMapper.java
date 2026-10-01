package org.vader.common.library.implementation.service.mapper;

import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.model.vader.dto.ClientPromptOutboxMessage;
import org.vader.common.model.vader.entity.ClientPromptOutboxMessageEntity;

/**
 * Maps {@link ClientPromptOutboxMessageEntity} to {@link ClientPromptOutboxMessage} DTOs, emitting
 * the carried prompt as a shallow id reference.
 */
@Service
@Transactional
public class ClientPromptOutboxMessageDtoMapper extends
    AbstractOutboxMessageDtoMapper<ClientPromptOutboxMessageEntity, ClientPromptOutboxMessage> {

    @Override
    public ClientPromptOutboxMessage map(final ClientPromptOutboxMessageEntity from) {
        ClientPromptOutboxMessage to = null;
        if (Objects.nonNull(from)) {
            to = new ClientPromptOutboxMessage();
            super.setOutboxMessageFields(from, to);
            if (Objects.nonNull(from.getClientPrompt())) {
                to.setClientPromptId(from.getClientPrompt().getId());
            }
        }
        return to;
    }
}
