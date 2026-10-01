package org.vader.common.model.vader.dto;

import org.vader.common.model.vader.entity.LlmRequestKind;

/**
 * DTO for a queue message carrying one call to the local LLM: the serialized request and, once
 * the call completes, the serialized response envelope.
 */
public class LlmRequestOutboxMessage extends AbstractOutboxMessage {

    private LlmRequestKind kind;

    private String requestJson;

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
