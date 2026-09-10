package org.vader.common.model.vader.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;

/**
 * An inbox/outbox queue message carrying a client prompt awaiting decomposition into a workflow.
 *
 * <p>Written by {@code ClientPromptOutbox} when a prompt is accepted; popped by
 * {@code ClientPromptInbox}, which runs the decomposition and settles the message.</p>
 */
@Entity
public class ClientPromptOutboxMessageEntity extends AbstractOutboxMessageEntity {

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "client_prompt_outbox_message_client_prompt_join_id")
    private ClientPromptEntity clientPrompt;

    @Override
    public String getModelType() {
        return "ClientPromptOutboxMessage";
    }

    public ClientPromptEntity getClientPrompt() {
        return this.clientPrompt;
    }

    public void setClientPrompt(ClientPromptEntity clientPrompt) {
        this.clientPrompt = clientPrompt;
    }
}
