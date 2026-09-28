package org.vader.core.server.exceptions;

/**
 * Thrown when a harness calls the agent control plane or inference gateway with an assignment id
 * that does not correspond to any {@code TaskAttempt}. Since the assignment id doubles as the
 * harness's bearer credential, this covers both "unknown id" and "not authorized" -- there is
 * nothing else to distinguish.
 */
public class UnknownAssignmentException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception with a message.
     *
     * @param message the detail message
     */
    public UnknownAssignmentException(final String message) {
        super(message);
    }
}
