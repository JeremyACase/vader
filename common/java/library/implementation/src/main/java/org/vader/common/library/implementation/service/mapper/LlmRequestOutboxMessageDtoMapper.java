package org.vader.common.library.implementation.service.mapper;

import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.model.vader.dto.LlmRequestOutboxMessage;
import org.vader.common.model.vader.entity.LlmRequestOutboxMessageEntity;

/**
 * Maps {@link LlmRequestOutboxMessageEntity} to {@link LlmRequestOutboxMessage} DTOs, including
 * the serialized request and response: they are the message's whole payload.
 */
@Service
@Transactional
public class LlmRequestOutboxMessageDtoMapper extends
    AbstractOutboxMessageDtoMapper<LlmRequestOutboxMessageEntity, LlmRequestOutboxMessage> {

    @Override
    public LlmRequestOutboxMessage map(final LlmRequestOutboxMessageEntity from) {
        LlmRequestOutboxMessage to = null;
        if (Objects.nonNull(from)) {
            to = new LlmRequestOutboxMessage();
            super.setOutboxMessageFields(from, to);
            to.setKind(from.getKind());
            to.setRequestJson(from.getRequestJson());
            to.setResponseJson(from.getResponseJson());
        }
        return to;
    }
}
