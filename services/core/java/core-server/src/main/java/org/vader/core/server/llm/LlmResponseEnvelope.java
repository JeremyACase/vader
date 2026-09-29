package org.vader.core.server.llm;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * What {@link LlmRequestInbox} writes back for any kind of LLM call: either the executor's
 * response, or why the LLM could not be reached.
 *
 * <p>Unreachable is a value rather than a {@code FAILED} message so {@link LlmRequestQueue} can
 * tell an outage (reported as {@code OrchestratorUnavailableException}) apart from a call the LLM
 * answered but that could not be processed.</p>
 *
 * @param value the executor's response as JSON, or {@code null} if unreachable
 * @param unreachableReason why the LLM could not be reached, or {@code null} on success
 */
public record LlmResponseEnvelope(JsonNode value, String unreachableReason) {

    /**
     * Wraps a successful response.
     *
     * @param value the executor's response as JSON
     * @return the envelope
     */
    public static LlmResponseEnvelope of(final JsonNode value) {
        return new LlmResponseEnvelope(value, null);
    }

    /**
     * Records that the LLM could not be reached.
     *
     * @param reason why
     * @return the envelope
     */
    public static LlmResponseEnvelope unreachable(final String reason) {
        return new LlmResponseEnvelope(null, reason);
    }

    /**
     * Whether the LLM could not be reached.
     *
     * @return {@code true} if there is no response to read
     */
    @JsonIgnore
    public boolean isUnreachable() {
        return this.unreachableReason != null;
    }
}
