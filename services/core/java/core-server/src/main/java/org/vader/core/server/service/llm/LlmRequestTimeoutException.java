package org.vader.core.server.service.llm;

/**
 * Thrown when a caller gives up waiting on {@link LlmRequestQueue} -- the queue looked stuck, or
 * the overall wait elapsed -- as opposed to a request that was processed and failed.
 *
 * <p>A distinct type because the two call for opposite handling: a timeout means the LLM is
 * unavailable right now and the same request is worth retrying later, whereas a processed-and-
 * failed request (e.g. the model's output could not be parsed) would likely just fail the same
 * way again.</p>
 */
public class LlmRequestTimeoutException extends LlmRequestQueueException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message the detail message
     */
    public LlmRequestTimeoutException(final String message) {
        super(message);
    }
}
