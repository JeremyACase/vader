package org.vader.core.server.service.config.pythonsandbox;

import java.util.Set;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.vader.core.server.service.registries.AgentToolAudience;
import org.vader.core.server.service.registries.ToolAudienceTag;
import org.vader.core.server.service.tools.pythonsandbox.PythonSandboxTools;

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

    /**
     * Deliberately offered to no internal agent: these ad-hoc tools (create, name, stage into,
     * run in, delete an arbitrary sandbox) remain for ops/MCP use only. A task agent gets
     * {@code TaskAttemptSandboxTools} instead, which needs no sandbox name at all -- having a
     * model carry a server-generated name between calls is exactly what it kept getting wrong.
     *
     * @param pythonSandboxToolCallbacks this config's own provider bean
     * @return the audience tag, with no audiences
     */
    @Bean
    public ToolAudienceTag pythonSandboxToolAudience(
        final ToolCallbackProvider pythonSandboxToolCallbacks) {
        return new ToolAudienceTag(pythonSandboxToolCallbacks, Set.<AgentToolAudience>of());
    }
}
