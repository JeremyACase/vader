package org.vader.core.server.exceptions;

/**
 * Thrown when a harness reports a heartbeat or result against an assignment whose
 * {@code TaskAttempt} has already reached a terminal status. Rejects the call rather than letting
 * a stale or duplicate report from a zombie harness overwrite a newer, already-settled outcome.
 */
public class AssignmentAlreadyTerminalException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception with a message.
     *
     * @param message the detail message
     */
    public AssignmentAlreadyTerminalException(final String message) {
        super(message);
    }
}
