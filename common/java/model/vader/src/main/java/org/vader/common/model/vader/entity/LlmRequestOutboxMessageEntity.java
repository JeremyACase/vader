package org.vader.common.model.vader.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Lob;
import jakarta.validation.constraints.NotNull;

/**
 * An inbox/outbox queue message carrying one outbound call to the local LLM.
 *
 * <p>Unlike {@link ClientPromptOutboxMessageEntity} or {@link TaskAssignmentOutboxMessageEntity},
 * there is no separate payload entity this message merely references -- the request itself is
 * self-contained in {@code requestJson}, since it must be replayable by whichever replica's inbox
 * happens to claim it, not only the replica that enqueued it. {@code responseJson} is filled in
 * by that same replica once the call completes, for the enqueuing replica (blocked, polling this
 * row) to read back.</p>
 */
@Entity
public class LlmRequestOutboxMessageEntity extends AbstractOutboxMessageEntity {

    @NotNull
    @Enumerated(EnumType.STRING)
    private LlmRequestKind kind;

    @Lob
    @NotNull
    private String requestJson;

    @Lob
    private String responseJson;

    @Override
    public String getModelType() {
        return "LlmRequestOutboxMessage";
    }

    public LlmRequestKind getKind() {
        return this.kind;
    }

    public void setKind(LlmRequestKind kind) {
        this.kind = kind;
    }

    public String getRequestJson() {
        return this.requestJson;
    }

    public void setRequestJson(String requestJson) {
        this.requestJson = requestJson;
    }

    public String getResponseJson() {
        return this.responseJson;
    }

    public void setResponseJson(String responseJson) {
        this.responseJson = responseJson;
    }
}
