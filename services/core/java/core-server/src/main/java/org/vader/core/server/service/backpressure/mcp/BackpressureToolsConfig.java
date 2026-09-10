package org.vader.core.server.service.backpressure.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link BackpressureTools} with the Spring AI MCP server.
 */
@Configuration
@ConditionalOnProperty(
    prefix = "vader.mcp.backpressure",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class BackpressureToolsConfig {

    /**
     * Exposes the back pressure tool methods as MCP tools.
     *
     * @param tools the annotated tool bean
     * @return a callback provider over its {@code @Tool} methods
     */
    @Bean
    public ToolCallbackProvider backpressureToolCallbacks(final BackpressureTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }
}
