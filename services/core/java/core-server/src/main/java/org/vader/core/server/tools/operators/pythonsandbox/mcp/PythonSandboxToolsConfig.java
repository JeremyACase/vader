package org.vader.core.server.tools.operators.pythonsandbox.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link PythonSandboxTools} with the Spring AI MCP server. The MCP server
 * auto-configuration discovers {@link ToolCallbackProvider} beans and publishes their tools over
 * the SSE transport.
 */
@Configuration
@ConditionalOnProperty(
    prefix = "vader.operators.python-sandbox",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false)
public class PythonSandboxToolsConfig {

    /**
     * Exposes the sandbox tool methods as MCP tools.
     *
     * @param tools the annotated tool bean
     * @return a callback provider over its {@code @Tool} methods
     */
    @Bean
    public ToolCallbackProvider pythonSandboxToolCallbacks(final PythonSandboxTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }
}
