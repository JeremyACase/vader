package org.vader.core.server.exceptions;

/**
 * Thrown when a harness asks {@code /vader/core-server/agent/tool-calls} to invoke a tool name
 * that does not match any tool currently registered in {@code McpToolCallbackRegistry} -- most
 * likely a model hallucinating a tool it was never actually offered.
 */
public class UnknownToolException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception with a message.
     *
     * @param message the detail message
     */
    public UnknownToolException(final String message) {
        super(message);
    }
}
