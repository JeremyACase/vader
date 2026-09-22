package org.vader.core.server.service.config.task;

import java.util.Set;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.vader.core.server.service.registries.AgentToolAudience;
import org.vader.core.server.service.registries.ToolAudienceTag;
import org.vader.core.server.service.tools.task.TaskUpdateTools;

/**
 * Registers {@link TaskUpdateTools} with the Spring AI MCP server.
 */
@Configuration
@ConditionalOnProperty(
    prefix = "vader.mcp.task-update",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class TaskUpdateToolsConfig {

    /**
     * Exposes the task-update tool methods as MCP tools.
     *
     * @param tools the annotated tool bean
     * @return a callback provider over its {@code @Tool} methods
     */
    @Bean
    public ToolCallbackProvider taskUpdateToolCallbacks(final TaskUpdateTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }

    /**
     * Leaving a progress note only ever makes sense for the task-execution agent actually
     * working the task; an orchestration agent has no task of its own to update.
     *
     * @param taskUpdateToolCallbacks this config's own provider bean
     * @return the audience tag
     */
    @Bean
    public ToolAudienceTag taskUpdateToolAudience(
        final ToolCallbackProvider taskUpdateToolCallbacks) {
        return new ToolAudienceTag(
            taskUpdateToolCallbacks, Set.of(AgentToolAudience.TASK_EXECUTION));
    }
}
