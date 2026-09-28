package org.vader.core.server.service.registries;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Flattens every {@link ToolCallbackProvider} bean registered in the application context into one
 * lookup table, so any collaborator that needs "every tool currently available" (or one tool by
 * name) has a single place to ask instead of re-flattening the provider list itself. Invocation
 * (looking a tool up by name to actually call it) is audience-independent -- once a model has
 * requested a tool call, {@link #findByName} resolves it regardless of who was offered it; the
 * audience only gates what a collaborator building a model's *available* tool list offers in the
 * first place, via {@link #forAudience}.
 */
@Service
public class McpToolCallbackRegistry {

    @Autowired
    private ObjectProvider<ToolCallbackProvider> toolCallbackProviders;

    @Autowired
    private ObjectProvider<ToolAudienceTag> audienceTags;

    private List<ToolCallback> toolCallbacks;

    private List<ToolAudienceTag> tags;

    /**
     * Flattens the registered providers into a single tool list once dependencies are injected.
     */
    @PostConstruct
    void wire() {
        this.toolCallbacks = this.toolCallbackProviders.stream()
            .flatMap(provider -> Arrays.stream(provider.getToolCallbacks()))
            .toList();
        this.tags = this.audienceTags.stream().toList();
    }

    /**
     * Returns every tool currently registered, across every {@link ToolCallbackProvider} bean,
     * regardless of {@link AgentToolAudience}. Only {@link #findByName} and {@link #forAudience}
     * should be used going forward -- this stays only for anything that genuinely needs the
     * unscoped set.
     *
     * @return the flattened tool list
     */
    public List<ToolCallback> all() {
        return this.toolCallbacks;
    }

    /**
     * Returns every tool tagged for the given {@link AgentToolAudience} -- what a collaborator
     * building a model's available-tool list for that kind of agent should offer it. A tool
     * group with no {@link ToolAudienceTag} registered for it (a bug, not an intentional
     * omission) is offered to no one, rather than silently falling back to everyone.
     *
     * @param audience which kind of agent is about to be offered tools
     * @return the tools tagged for that audience
     */
    public List<ToolCallback> forAudience(final AgentToolAudience audience) {
        return this.tags.stream()
            .filter(tag -> tag.audiences().contains(audience))
            .flatMap(tag -> Arrays.stream(tag.provider().getToolCallbacks()))
            .toList();
    }

    /**
     * Looks up one tool by its MCP name.
     *
     * @param name the tool name, as returned by {@code ToolDefinition.name()}
     * @return the matching tool, or empty if no registered tool has that name
     */
    public Optional<ToolCallback> findByName(final String name) {
        return this.toolCallbacks.stream()
            .filter(callback -> callback.getToolDefinition().name().equals(name))
            .findFirst();
    }
}
