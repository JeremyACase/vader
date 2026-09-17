package org.vader.core.server.service.config.logging;

import java.util.Arrays;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import org.vader.core.server.service.decorators.tools.LoggingToolCallbackDecorator;

/**
 * Wraps every {@link ToolCallback} produced by every {@link ToolCallbackProvider} bean in a
 * {@link LoggingToolCallbackDecorator}, so every MCP tool in the codebase logs its invocations
 * without each feature's tool class or {@code *ToolsConfig} having to do it itself. Applies
 * uniformly to method-based tools (e.g. {@code PythonSandboxTools}), programmatically-built
 * function tools (e.g. the per-entity DAO tools), and any {@link ToolCallbackProvider} added in
 * the future.
 */
@Component
public class McpToolCallLoggingPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(final Object bean, final String beanName) {
        var result = bean;
        if (bean instanceof ToolCallbackProvider provider) {
            result = wrapWithLogging(provider);
        }
        return result;
    }

    private static ToolCallbackProvider wrapWithLogging(final ToolCallbackProvider provider) {
        var loggingCallbacks = Arrays.stream(provider.getToolCallbacks())
            .map(LoggingToolCallbackDecorator::new)
            .toArray(ToolCallback[]::new);
        return () -> loggingCallbacks;
    }
}
