package org.vader.core.server.service.config.storage;

import java.util.Set;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.vader.core.server.service.registries.AgentToolAudience;
import org.vader.core.server.service.registries.ToolAudienceTag;
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

    /**
     * Reading an uploaded object's content is only ever relevant to the task-execution agent
     * actually working on the prompt that object was attached to.
     *
     * @param objectStorageToolCallbacks this config's own provider bean
     * @return the audience tag
     */
    @Bean
    public ToolAudienceTag objectStorageToolAudience(
        final ToolCallbackProvider objectStorageToolCallbacks) {
        return new ToolAudienceTag(
            objectStorageToolCallbacks, Set.of(AgentToolAudience.TASK_EXECUTION));
    }
}
