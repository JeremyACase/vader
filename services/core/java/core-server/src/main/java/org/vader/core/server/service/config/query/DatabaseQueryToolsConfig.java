package org.vader.core.server.service.config.query;

import java.util.Set;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.vader.core.server.service.registries.AgentToolAudience;
import org.vader.core.server.service.registries.ToolAudienceTag;
import org.vader.core.server.service.tools.query.DatabaseQueryTools;

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

    /**
     * Not needed by a task-execution agent -- a task's own context (the original request, any
     * attached files, prerequisite results) is already composed for it by
     * {@code TaskAgentService.contextFor}. Reserved for an orchestration agent looking up
     * system state to plan or decide with.
     *
     * @param databaseQueryToolCallbacks this config's own provider bean
     * @return the audience tag
     */
    @Bean
    public ToolAudienceTag databaseQueryToolAudience(
        final ToolCallbackProvider databaseQueryToolCallbacks) {
        return new ToolAudienceTag(
            databaseQueryToolCallbacks, Set.of(AgentToolAudience.ORCHESTRATION));
    }
}
