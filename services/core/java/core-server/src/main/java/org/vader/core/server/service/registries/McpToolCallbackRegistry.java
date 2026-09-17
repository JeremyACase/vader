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
 * name) has a single place to ask instead of re-flattening the provider list itself. Shared by
 * {@code LocalLlmOrchestrationStrategy} (decomposition) and every
 * {@code InterfaceInferenceGatewayStrategy} (per-task execution turns).
 */
@Service
public class McpToolCallbackRegistry {

    @Autowired
    private ObjectProvider<ToolCallbackProvider> toolCallbackProviders;

    private List<ToolCallback> toolCallbacks;

    /**
     * Flattens the registered providers into a single tool list once dependencies are injected.
     */
    @PostConstruct
    void wire() {
        this.toolCallbacks = this.toolCallbackProviders.stream()
            .flatMap(provider -> Arrays.stream(provider.getToolCallbacks()))
            .toList();
    }

    /**
     * Returns every tool currently registered, across every {@link ToolCallbackProvider} bean.
     *
     * @return the flattened tool list
     */
    public List<ToolCallback> all() {
        return this.toolCallbacks;
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
