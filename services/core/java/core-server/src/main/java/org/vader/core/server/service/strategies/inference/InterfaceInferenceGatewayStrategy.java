package org.vader.core.server.service.strategies.inference;

import java.util.List;
import org.vader.core.server.models.ConversationMessage;
import org.vader.core.server.models.InferenceTurn;
import org.vader.core.server.service.agent.orchestrator.strategies.interfaces.InterfaceLlmOrchestrationStrategy;

/**
 * The only path from {@code /vader/core-server/agent/inference} to a model: this, not the
 * agent-harness process, is where provider config lives and where every call is attributable and
 * budget-enforceable. Mirrors {@code InterfaceLlmOrchestrationStrategy}'s split, but for a running
 * multi-turn conversation rather than a one-shot structured decomposition.
 */
public interface InterfaceInferenceGatewayStrategy {

    /**
     * Completes one turn.
     *
     * @param messages the running conversation so far
     * @return the model's response: either a final answer, or a request to call one or more
     *     tools before it can continue
     */
    InferenceTurn complete(List<ConversationMessage> messages);
}
