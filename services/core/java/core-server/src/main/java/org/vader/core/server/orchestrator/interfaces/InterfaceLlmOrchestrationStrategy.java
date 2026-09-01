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
     * Sends a client prompt to the backing LLM and returns its response.
     *
     * @param clientPrompt the prompt to send
     * @return the LLM's response text
     */
    String orchestrate(ClientPrompt clientPrompt);
}
