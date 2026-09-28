package org.vader.core.server.service.registries;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

class McpToolCallbackRegistryTest {

    private McpToolCallbackRegistry registry;

    private static ToolCallback callbackNamed(final String name) {
        var toolDefinition = mock(ToolDefinition.class);
        when(toolDefinition.name()).thenReturn(name);
        var callback = mock(ToolCallback.class);
        when(callback.getToolDefinition()).thenReturn(toolDefinition);
        return callback;
    }

    private void wireProviders(final ToolCallbackProvider... providers) {
        this.wireProvidersWithAudiences(Map.of(), providers);
    }

    /**
     * Wires the given providers, tagging each with whatever {@link AgentToolAudience}s
     * {@code audiencesByProvider} maps it to (an untagged provider -- the common case for tests
     * that only care about {@link McpToolCallbackRegistry#all} / {@link
     * McpToolCallbackRegistry#findByName} -- is simply invisible to {@link
     * McpToolCallbackRegistry#forAudience}).
     */
    @SuppressWarnings("unchecked")
    private void wireProvidersWithAudiences(
            final Map<ToolCallbackProvider, Set<AgentToolAudience>> audiencesByProvider,
            final ToolCallbackProvider... providers) {
        var objectProvider = mock(ObjectProvider.class);
        when(objectProvider.stream()).thenAnswer(invocation -> List.of(providers).stream());

        var tags = audiencesByProvider.entrySet().stream()
            .map(entry -> new ToolAudienceTag(entry.getKey(), entry.getValue()))
            .toList();
        var tagProvider = mock(ObjectProvider.class);
        when(tagProvider.stream()).thenAnswer(invocation -> tags.stream());

        this.registry = new McpToolCallbackRegistry();
        ReflectionTestUtils.setField(this.registry, "toolCallbackProviders", objectProvider);
        ReflectionTestUtils.setField(this.registry, "audienceTags", tagProvider);
        ReflectionTestUtils.invokeMethod(this.registry, "wire");
    }

    @BeforeEach
    void setUp() {
        var callback = callbackNamed("run_python_code");
        this.wireProviders(() -> new ToolCallback[] {callback});
    }

    @Test
    void all_flattensEveryProvidersCallbacks() {
        var first = callbackNamed("query_database");
        var second = callbackNamed("get_backpressure");
        this.wireProviders(
            () -> new ToolCallback[] {first},
            () -> new ToolCallback[] {second});

        assertThat(this.registry.all()).containsExactly(first, second);
    }

    @Test
    void findByName_returnsTheMatchingTool() {
        assertThat(this.registry.findByName("run_python_code")).isPresent();
    }

    @Test
    void findByName_forAnUnregisteredName_returnsEmpty() {
        assertThat(this.registry.findByName("bogus_tool")).isEmpty();
    }

    @Test
    void forAudience_returnsOnlyTheTaggedProvidersCallbacks() {
        var sandboxCallback = callbackNamed("run_python_code");
        var backpressureCallback = callbackNamed("get_backpressure");
        ToolCallbackProvider sandboxProvider = () -> new ToolCallback[] {sandboxCallback};
        ToolCallbackProvider backpressureProvider = () -> new ToolCallback[] {backpressureCallback};
        this.wireProvidersWithAudiences(
            Map.of(
                sandboxProvider, Set.of(AgentToolAudience.TASK_EXECUTION),
                backpressureProvider, Set.of(AgentToolAudience.ORCHESTRATION)),
            sandboxProvider, backpressureProvider);

        assertThat(this.registry.forAudience(AgentToolAudience.TASK_EXECUTION))
            .containsExactly(sandboxCallback);
        assertThat(this.registry.forAudience(AgentToolAudience.ORCHESTRATION))
            .containsExactly(backpressureCallback);
    }

    @Test
    void forAudience_includesTheProviderTaggedForBothKinds() {
        var callback = callbackNamed("query_database");
        ToolCallbackProvider provider = () -> new ToolCallback[] {callback};
        this.wireProvidersWithAudiences(
            Map.of(provider,
                Set.of(AgentToolAudience.TASK_EXECUTION, AgentToolAudience.ORCHESTRATION)),
            provider);

        assertThat(this.registry.forAudience(AgentToolAudience.TASK_EXECUTION))
            .containsExactly(callback);
        assertThat(this.registry.forAudience(AgentToolAudience.ORCHESTRATION))
            .containsExactly(callback);
    }

    @Test
    void forAudience_omitsAnUntaggedProvider() {
        this.wireProviders(() -> new ToolCallback[] {callbackNamed("run_python_code")});

        assertThat(this.registry.forAudience(AgentToolAudience.TASK_EXECUTION)).isEmpty();
    }
}
