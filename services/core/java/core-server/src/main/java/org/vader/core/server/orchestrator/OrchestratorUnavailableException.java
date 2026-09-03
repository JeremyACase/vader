package org.vader.core.server.orchestrator;

/**
 * Thrown when the orchestrator LLM cannot be reached at all -- connection refused, timed out, or a
 * transport-level error. Distinct from {@link OrchestratorResponseException}, which means the LLM
 * answered but the answer was unusable. This condition is typically transient (the backend is still
 * warming up), so callers may retry.
 */
public class OrchestratorUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception with a message and underlying cause.
     *
     * @param message the detail message
     * @param cause the underlying transport failure
     */
    public OrchestratorUnavailableException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
