package org.vader.core.server.service.llm;

/**
 * A request submitted through {@link LlmRequestQueue} ended without a usable response: either
 * whichever replica processed it recorded a failure, or nothing processed it before the caller's
 * wait timed out. Both existing callers already catch {@link RuntimeException} broadly and
 * translate it into their own domain exception, so this deliberately carries no more structure
 * than that.
 */
public class LlmRequestQueueException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception with a message only -- used when a claimed message settled
     * {@code FAILED} (the failure reason is already text) or when the wait timed out.
     *
     * @param message the detail message
     */
    public LlmRequestQueueException(final String message) {
        super(message);
    }

    /**
     * Creates the exception with a message and underlying cause -- used when serializing or
     * deserializing the request/response JSON itself fails.
     *
     * @param message the detail message
     * @param cause the underlying failure
     */
    public LlmRequestQueueException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
