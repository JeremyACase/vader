package org.vader.core.server.llm;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.vader.common.model.vader.entity.LlmRequestKind;
import org.vader.common.model.vader.entity.LlmRequestOutboxMessageEntity;
import org.vader.common.model.vader.entity.OutboxMessageStatus;

/**
 * Creates the queue row for one LLM call, stamped with what will shape its answer -- the
 * fingerprint of its kind's prompt templates and the model it goes to -- so every recorded call
 * can be attributed to the exact prompt version and model that produced it.
 */
@Component
public class LlmRequestOutboxMessageFactory {

    @Autowired
    private PromptTemplateRegistry promptTemplateRegistry;

    @Autowired
    private LlmModelNameResolver llmModelNameResolver;

    /**
     * Creates a pending, unsaved message.
     *
     * @param kind the call's kind
     * @param requestJson the serialized request
     * @return the message, ready to save
     */
    public LlmRequestOutboxMessageEntity create(
            final LlmRequestKind kind, final String requestJson) {
        var message = new LlmRequestOutboxMessageEntity();
        message.setKind(kind);
        message.setRequestJson(requestJson);
        message.setStatus(OutboxMessageStatus.PENDING);
        message.setPromptFingerprint(this.promptTemplateRegistry.fingerprint(kind));
        message.setModel(this.llmModelNameResolver.modelName());
        return message;
    }
}
