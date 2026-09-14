package org.vader.core.server.service.config.storage;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.vader.core.server.service.tools.storage.ObjectStorageTools;

/**
 * Registers {@link ObjectStorageTools} with the Spring AI MCP server.
 */
@Configuration
@ConditionalOnProperty(
    prefix = "vader.mcp.object-storage",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ObjectStorageToolsConfig {

    /**
     * Exposes the object-storage tool methods as MCP tools.
     *
     * @param tools the annotated tool bean
     * @return a callback provider over its {@code @Tool} methods
     */
    @Bean
    public ToolCallbackProvider objectStorageToolCallbacks(final ObjectStorageTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }
}
