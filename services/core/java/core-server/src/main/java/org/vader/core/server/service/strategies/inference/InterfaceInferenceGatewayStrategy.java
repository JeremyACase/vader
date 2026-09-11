package org.vader.core.server.service.strategies.inference;

import org.vader.core.server.models.InferenceTurn;
import org.vader.core.server.service.strategies.orchestration.interfaces.InterfaceLlmOrchestrationStrategy;

/**
 * The only path from {@code /vader/core-server/agent/inference} to a model: this, not the
 * agent-harness process, is where provider config lives and where every call is attributable and
 * budget-enforceable. Mirrors {@code InterfaceLlmOrchestrationStrategy}'s split, but for a single
 * free-form turn rather than a structured decomposition.
 */
public interface InterfaceInferenceGatewayStrategy {

    /**
     * Completes one turn.
     *
     * @param prompt the prompt for this turn
     * @return the model's response and its token cost
     */
    InferenceTurn complete(String prompt);
}
