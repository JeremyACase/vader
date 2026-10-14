package org.vader.core.server.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Names the model every LLM call goes to, for recording alongside what it produced. */
@Component
public class LlmModelNameResolver {

    @Value("${vader.orchestrator.type:local}")
    private String orchestratorType;

    @Value("${spring.ai.ollama.chat.options.model:}")
    private String localModel;

    /**
     * The model's name.
     *
     * @return the local model's name, or the orchestrator type (e.g. {@code scripted}) when no
     *     local model answers
     */
    public String modelName() {
        return "local".equals(this.orchestratorType) ? this.localModel : this.orchestratorType;
    }
}
