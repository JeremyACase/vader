package org.vader.core.server.workflow;

/** Thrown when a request names a workflow id that does not exist. */
public class UnknownWorkflowException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception with a message.
     *
     * @param message the detail message
     */
    public UnknownWorkflowException(final String message) {
        super(message);
    }
}
