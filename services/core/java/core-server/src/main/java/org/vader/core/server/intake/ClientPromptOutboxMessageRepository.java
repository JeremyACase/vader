package org.vader.core.server.intake;

import org.vader.common.model.vader.entity.ClientPromptOutboxMessageEntity;
import org.vader.core.server.messaging.OutboxMessageRepository;

/** Spring Data repository for {@link ClientPromptOutboxMessageEntity} queue messages. */
public interface ClientPromptOutboxMessageRepository
    extends OutboxMessageRepository<ClientPromptOutboxMessageEntity> {
}
