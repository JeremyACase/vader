package org.vader.core.server.orchestrator.interfaces;

import org.vader.common.model.vader.dto.ClientPrompt;

/**
 * Strategy for coordinating RESTful traffic between the core server and an LLM backend.
 *
 * <p>The active implementation is selected by Spring based on the {@code vader.orchestrator.type}
 * property.</p>
 */
public interface InterfaceLlmOrchestrationStrategy {

    /**
     * Asks the backing LLM to decompose a client prompt into a task plan.
     *
     * @param clientPrompt the prompt to decompose
     * @return the LLM's raw response, expected to be a JSON task plan
     */
    String orchestrate(ClientPrompt clientPrompt);
}
