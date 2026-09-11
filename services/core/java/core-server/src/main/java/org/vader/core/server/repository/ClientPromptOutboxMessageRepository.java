package org.vader.core.server.repository;

import org.vader.common.model.vader.entity.ClientPromptOutboxMessageEntity;

/** Spring Data repository for {@link ClientPromptOutboxMessageEntity} queue messages. */
public interface ClientPromptOutboxMessageRepository
    extends OutboxMessageRepository<ClientPromptOutboxMessageEntity> {
}
