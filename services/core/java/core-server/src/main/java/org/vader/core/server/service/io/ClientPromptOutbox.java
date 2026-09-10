package org.vader.core.server.service.io;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.vader.common.model.vader.entity.ClientPromptEntity;
import org.vader.common.model.vader.entity.ClientPromptOutboxMessageEntity;
import org.vader.core.server.repository.ClientPromptOutboxMessageRepository;

/**
 * Outbox for client prompts: writes a queue message for each accepted prompt so
 * {@link ClientPromptInbox} can later decompose it into a workflow.
 */
@Service
public class ClientPromptOutbox
    extends AbstractOutbox<ClientPromptEntity, ClientPromptOutboxMessageEntity> {

    @Autowired
    private ClientPromptOutboxMessageRepository messageRepository;

    @Override
    protected ClientPromptOutboxMessageEntity newMessage(final ClientPromptEntity payload) {
        var message = new ClientPromptOutboxMessageEntity();
        message.setClientPrompt(payload);
        return message;
    }

    @Override
    protected JpaRepository<ClientPromptOutboxMessageEntity, String> repository() {
        return this.messageRepository;
    }

    @Override
    protected String queuedModelType() {
        return "ClientPrompt";
    }
}
