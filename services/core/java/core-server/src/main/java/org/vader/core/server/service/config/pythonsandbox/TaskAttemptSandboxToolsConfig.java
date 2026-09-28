package org.vader.core.server.service.config.pythonsandbox;

import java.util.Set;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.vader.core.server.service.registries.AgentToolAudience;
import org.vader.core.server.service.registries.ToolAudienceTag;
import org.vader.core.server.service.tools.pythonsandbox.TaskAttemptSandboxTools;

/**
 * Registers {@link TaskAttemptSandboxTools} as a tool provider and offers it to task agents.
 *
 * <p>It must be a {@link ToolCallbackProvider} bean for {@code McpToolCallbackRegistry#findByName}
 * to resolve it when a harness invokes it, which also means Spring AI's MCP server publishes it.
 * An external MCP client calling it just gets an error: its call carries no task attempt, so
 * {@code TaskAttemptToolContext} rejects it before anything runs.</p>
 */
@Configuration
@ConditionalOnProperty(
    name = {"vader.operators.enabled", "vader.operators.python-sandbox.enabled"},
    havingValue = "true",
    matchIfMissing = false)
public class TaskAttemptSandboxToolsConfig {

    /**
     * Exposes the attempt-scoped sandbox tool.
     *
     * @param tools the annotated tool bean
     * @return a callback provider over its {@code @Tool} methods
     */
    @Bean
    public ToolCallbackProvider taskAttemptSandboxToolCallbacks(
        final TaskAttemptSandboxTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }

    /**
     * Offered only to a per-task {@code core-agent-harness} run.
     *
     * @param taskAttemptSandboxToolCallbacks this config's own provider bean
     * @return the audience tag
     */
    @Bean
    public ToolAudienceTag taskAttemptSandboxToolAudience(
        final ToolCallbackProvider taskAttemptSandboxToolCallbacks) {
        return new ToolAudienceTag(
            taskAttemptSandboxToolCallbacks, Set.of(AgentToolAudience.TASK_EXECUTION));
    }
}
