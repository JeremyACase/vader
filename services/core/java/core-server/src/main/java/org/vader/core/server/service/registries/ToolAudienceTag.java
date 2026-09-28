package org.vader.core.server.service.registries;

import java.util.Set;
import org.springframework.ai.tool.ToolCallbackProvider;

/**
 * Declares which {@link AgentToolAudience}(s) a {@link ToolCallbackProvider}'s tools are offered
 * to. Each {@code *ToolsConfig} class registers one of these alongside its existing
 * {@code ToolCallbackProvider} bean -- the provider bean itself is untouched (and keeps being
 * auto-discovered by Spring AI's MCP server for the SSE endpoint); this is purely additional
 * metadata {@link McpToolCallbackRegistry#forAudience} reads to decide what an internally-spawned
 * agent is offered.
 *
 * @param provider the tagged tool group
 * @param audiences which agent kind(s) may be offered these tools
 */
public record ToolAudienceTag(ToolCallbackProvider provider, Set<AgentToolAudience> audiences) {
}
