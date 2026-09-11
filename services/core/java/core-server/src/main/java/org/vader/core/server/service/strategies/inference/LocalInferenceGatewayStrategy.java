package org.vader.core.server.service.strategies.inference;

import jakarta.annotation.PostConstruct;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.vader.core.exceptions.OrchestratorUnavailableException;
import org.vader.core.server.models.InferenceTurn;

/**
 * Completes a turn against the in-cluster Ollama instance via Spring AI's {@link ChatClient}.
 * Active only when {@code vader.orchestrator.type} is {@code local}.
 *
 * <p>Unlike the decomposition orchestrator, this issues no tool calls and expects no structured
 * output -- a harness's own action loop lives in the harness process, not here. This is
 * deliberately the only place in {@code core-server} (besides the decomposition orchestrator)
 * that ever calls an LLM directly; the harness reaches it exclusively through
 * {@code /vader/core-server/agent/inference}.</p>
 */
@Service
@ConditionalOnProperty(prefix = "vader.orchestrator", name = "type", havingValue = "local")
public class LocalInferenceGatewayStrategy implements InterfaceInferenceGatewayStrategy {

    private static final Logger logger =
        LoggerFactory.getLogger(LocalInferenceGatewayStrategy.class);

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    private ChatClient chatClient;

    /**
     * Builds the chat client once dependencies are injected.
     */
    @PostConstruct
    void wire() {
        this.chatClient = this.chatClientBuilder.build();
    }

    @Override
    public InferenceTurn complete(final String prompt) {
        try {
            var responseSpec = this.chatClient.prompt().user(prompt).call();
            var content = responseSpec.content();
            var tokensSpent = this.tokensSpent(responseSpec.chatResponse());
            return new InferenceTurn(content, tokensSpent);
        } catch (RuntimeException e) {
            logger.warn("Local LLM inference call failed: {}", e.getMessage());
            throw new OrchestratorUnavailableException("Could not reach the local LLM.", e);
        }
    }

    private long tokensSpent(final ChatResponse chatResponse) {
        if (Objects.isNull(chatResponse) || Objects.isNull(chatResponse.getMetadata())) {
            return 0L;
        }
        var usage = chatResponse.getMetadata().getUsage();
        if (Objects.isNull(usage) || Objects.isNull(usage.getTotalTokens())) {
            return 0L;
        }
        return usage.getTotalTokens();
    }
}
