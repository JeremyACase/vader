package org.vader.core.server.service.strategies.inference;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.vader.core.server.exceptions.OrchestratorUnavailableException;
import org.vader.core.server.models.ConversationMessage;
import org.vader.core.server.models.InferenceTurn;
import org.vader.core.server.service.llm.LlmRequestQueue;

/**
 * Completes a turn against the in-cluster Ollama instance. Active only when
 * {@code vader.orchestrator.type} is {@code local}.
 *
 * <p>Purely a thin proxy: this enqueues the turn onto {@link LlmRequestQueue} and blocks until
 * whichever replica's inbox claims and processes it -- possibly this one, possibly another --
 * writes a response back. It never talks to Ollama directly (that's
 * {@code InferenceTurnLlmExecutor}, called only from inside the inbox), which is what makes "only
 * one request in flight against Ollama system-wide at a time" a database-enforced invariant
 * rather than something this bean has to coordinate on its own.</p>
 */
@Service
@ConditionalOnProperty(prefix = "vader.orchestrator", name = "type", havingValue = "local")
public class LocalInferenceGatewayStrategy implements InterfaceInferenceGatewayStrategy {

    private static final Logger logger =
        LoggerFactory.getLogger(LocalInferenceGatewayStrategy.class);

    @Autowired
    private LlmRequestQueue requestQueue;

    @Override
    public InferenceTurn complete(final List<ConversationMessage> messages) {
        try {
            return this.requestQueue.submitInferenceTurn(messages);
        } catch (RuntimeException e) {
            logger.warn("Local LLM inference call failed: {}", e.getMessage());
            throw new OrchestratorUnavailableException("Could not reach the local LLM.", e);
        }
    }
}
