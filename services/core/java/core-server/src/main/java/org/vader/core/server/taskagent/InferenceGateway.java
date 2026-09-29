package org.vader.core.server.taskagent;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.vader.core.server.llm.LlmRequestQueue;
import org.vader.core.server.llm.OrchestratorUnavailableException;
import org.vader.core.server.taskagent.model.ConversationMessage;
import org.vader.core.server.taskagent.model.InferenceTurn;

/**
 * The only path from {@code /vader/core-server/agent/inference} to a model: this, not the
 * agent-harness process, is where provider config lives and where every call is attributable and
 * budget-enforceable.
 *
 * <p>Each turn goes through {@link LlmRequestQueue} to {@link InferenceTurnLlmExecutor}. Any
 * failure is reported as {@link OrchestratorUnavailableException}, carrying the underlying reason
 * (unreachable, or timed out waiting on the queue).</p>
 */
@Service
public class InferenceGateway {

    private static final Logger logger = LoggerFactory.getLogger(InferenceGateway.class);

    @Autowired
    private LlmRequestQueue requestQueue;

    /**
     * Completes one turn.
     *
     * @param messages the running conversation so far
     * @return the model's response: either a final answer, or a request to call one or more
     *     tools before it can continue
     */
    public InferenceTurn complete(final List<ConversationMessage> messages) {
        try {
            return this.requestQueue.submit(InferenceTurnLlmExecutor.class, messages);
        } catch (RuntimeException e) {
            logger.warn("Local LLM inference call failed: {}", e.getMessage());
            throw new OrchestratorUnavailableException(
                "The local LLM call failed: " + e.getMessage(), e);
        }
    }
}
