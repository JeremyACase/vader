package org.vader.common.model.vader.dto;

/**
 * DTO for a queue message carrying a client prompt awaiting decomposition. The prompt is a
 * shallow id reference.
 */
public class ClientPromptOutboxMessage extends AbstractOutboxMessage {

    private String clientPromptId;

    @Override
    public String getModelType() {
        return "ClientPromptOutboxMessage";
    }

    public String getClientPromptId() {
        return this.clientPromptId;
    }

    public void setClientPromptId(String clientPromptId) {
        this.clientPromptId = clientPromptId;
    }
}
