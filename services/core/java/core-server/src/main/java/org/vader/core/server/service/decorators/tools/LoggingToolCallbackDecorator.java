package org.vader.core.server.service.decorators.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * Decorator over a {@link ToolCallback} that logs every invocation -- the tool name, its input,
 * and either the resulting output or the thrown exception -- so an agent actuating an MCP tool
 * always leaves a trace, regardless of which concrete {@link ToolCallback} implementation
 * (method-based, function-based, or hand-built) is doing the work.
 *
 * <p>Input and output are truncated before logging: several tools (e.g. {@code get_object_content}
 * , {@code run_python_code}) carry base64-encoded file content that can run to megabytes, which
 * would otherwise flood the log for no diagnostic benefit.</p>
 */
public class LoggingToolCallbackDecorator implements ToolCallback {

    private static final Logger logger =
        LoggerFactory.getLogger(LoggingToolCallbackDecorator.class);

    private static final int MAX_LOGGED_CHARS = 500;

    private final ToolCallback delegate;

    /**
     * Wraps a tool callback with invocation logging.
     *
     * @param delegate the callback to log around and delegate every call to
     */
    public LoggingToolCallbackDecorator(final ToolCallback delegate) {
        this.delegate = delegate;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return this.delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return this.delegate.getToolMetadata();
    }

    @Override
    public String call(final String toolInput) {
        return this.call(toolInput, null);
    }

    @Override
    public String call(final String toolInput, final ToolContext toolContext) {
        var toolName = this.getToolDefinition().name();
        logger.info("MCP tool '{}' invoked with input: {}", toolName, truncate(toolInput));
        var result = this.invokeAndLogOutcome(toolName, toolInput, toolContext);
        return result;
    }

    private String invokeAndLogOutcome(
            final String toolName, final String toolInput, final ToolContext toolContext) {
        String result;
        try {
            result = this.delegateCall(toolInput, toolContext);
            logger.info("MCP tool '{}' completed with output: {}", toolName, truncate(result));
        } catch (RuntimeException e) {
            logger.warn("MCP tool '{}' failed: {}", toolName, e.getMessage(), e);
            throw e;
        }
        return result;
    }

    private String delegateCall(final String toolInput, final ToolContext toolContext) {
        return toolContext == null
            ? this.delegate.call(toolInput)
            : this.delegate.call(toolInput, toolContext);
    }

    private static String truncate(final String value) {
        var result = value;
        if (value != null && value.length() > MAX_LOGGED_CHARS) {
            result = value.substring(0, MAX_LOGGED_CHARS) + "...(truncated, "
                + value.length() + " chars total)";
        }
        return result;
    }
}
