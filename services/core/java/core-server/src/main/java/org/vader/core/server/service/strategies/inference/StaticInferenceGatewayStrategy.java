package org.vader.core.server.service.strategies.inference;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.vader.core.server.models.InferenceTurn;
import org.vader.core.server.service.strategies.orchestration.StaticLlmOrchestrationStrategy;

/**
 * Returns a fixed, canned turn -- no LLM, no network call. Active whenever
 * {@code vader.orchestrator.type} is {@code static}, mirroring
 * {@code StaticLlmOrchestrationStrategy}: the same switch that keeps decomposition deterministic
 * for {@code helm test} / CI also keeps every harness inference call deterministic, with no
 * Ollama in the cluster.
 */
@Service
@ConditionalOnProperty(prefix = "vader.orchestrator", name = "type", havingValue = "static")
public class StaticInferenceGatewayStrategy implements InterfaceInferenceGatewayStrategy {

    private static final String CANNED_RESPONSE =
        "Static inference response (vader.orchestrator.type=static; no LLM was called).";

    private static final long CANNED_TOKEN_COST = 10L;

    @Override
    public InferenceTurn complete(final String prompt) {
        return new InferenceTurn(CANNED_RESPONSE, CANNED_TOKEN_COST);
    }
}
