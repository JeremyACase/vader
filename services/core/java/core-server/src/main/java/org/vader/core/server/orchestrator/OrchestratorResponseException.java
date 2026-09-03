package org.vader.core.server.orchestrator;

/**
 * Thrown when the orchestrator LLM's response is missing, cannot be parsed as a task plan, or
 * fails the task-plan schema. Signals that no problem decomposition could be derived from the
 * response, so nothing is persisted.
 */
public class OrchestratorResponseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception with a message.
     *
     * @param message the detail message
     */
    public OrchestratorResponseException(final String message) {
        super(message);
    }

    /**
     * Creates the exception with a message and underlying cause.
     *
     * @param message the detail message
     * @param cause the underlying cause
     */
    public OrchestratorResponseException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
