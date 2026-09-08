package org.vader.core.server.tools.query.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link DatabaseQueryTools} with the Spring AI MCP server.
 */
@Configuration
@ConditionalOnProperty(
    prefix = "vader.mcp.database-query",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class DatabaseQueryToolsConfig {

    /**
     * Exposes the database-query tool methods as MCP tools.
     *
     * @param tools the annotated tool bean
     * @return a callback provider over its {@code @Tool} methods
     */
    @Bean
    public ToolCallbackProvider databaseQueryToolCallbacks(final DatabaseQueryTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }
}
