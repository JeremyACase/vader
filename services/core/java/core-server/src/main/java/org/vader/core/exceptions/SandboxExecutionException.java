package org.vader.core.exceptions;

/**
 * Thrown when a code-execution request to a Python sandbox pod's own HTTP server fails --
 * unreachable, timed out at the transport level, or a non-2xx response. Distinct from a
 * successful run that itself timed out or exited non-zero, which is a normal
 * {@code SandboxExecutionResult}, not an exception.
 */
public class SandboxExecutionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception with a message and underlying cause.
     *
     * @param message the detail message
     * @param cause the underlying transport failure
     */
    public SandboxExecutionException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
