package org.vader.core.server.service.config.backpressure;

import java.util.Set;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.vader.core.server.service.registries.AgentToolAudience;
import org.vader.core.server.service.registries.ToolAudienceTag;
import org.vader.core.server.service.tools.backpressure.BackpressureTools;

/**
 * Registers {@link BackpressureTools} with the Spring AI MCP server.
 */
@Configuration
@ConditionalOnProperty(
    prefix = "vader.mcp.backpressure",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false)
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

    /**
     * Back pressure is system-wide operational state, relevant to a higher-level orchestration
     * agent deciding whether to alleviate it (e.g. by provisioning more resources) -- never to an
     * agent solving one task-graph subtask.
     *
     * @param backpressureToolCallbacks this config's own provider bean
     * @return the audience tag
     */
    @Bean
    public ToolAudienceTag backpressureToolAudience(
        final ToolCallbackProvider backpressureToolCallbacks) {
        return new ToolAudienceTag(
            backpressureToolCallbacks, Set.of(AgentToolAudience.ORCHESTRATION));
    }
}
