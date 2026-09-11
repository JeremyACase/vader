package org.vader.common.model.vader;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Lightweight receipt returned from "accept and persist" endpoints instead of the fully processed
 * result.
 *
 * <p>When a request is accepted for asynchronous processing, the caller gets back the id and type
 * of the record that was persisted -- enough to poll for the eventual result -- rather than
 * waiting for that result inline. Not an {@link org.vader.common.model.vader.dto.AbstractModel}:
 * it carries no audit fields and is never itself persisted or queried.</p>
 */
public class IngressResponse {

    private String id = null;

    private String modelType = "IngressResponse";

    private String payloadModelType = "IngressResponse";

    /**
     * The id assigned to the persisted payload record.
     *
     * @return the payload id
     */
    @Pattern(regexp = "[a-f0-9]{8}(?:-[a-f0-9]{4}){4}[a-f0-9]{8}")
    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /**
     * The type of this response model.
     *
     * @return always {@code "IngressResponse"}
     */
    @NotNull
    public String getModelType() {
        return this.modelType;
    }

    public void setModelType(String modelType) {
        this.modelType = modelType;
    }

    /**
     * The {@code modelType} of the persisted payload record (e.g. {@code "ClientPrompt"}).
     *
     * @return the payload model type
     */
    public String getPayloadModelType() {
        return this.payloadModelType;
    }

    public void setPayloadModelType(String payloadModelType) {
        this.payloadModelType = payloadModelType;
    }
}
