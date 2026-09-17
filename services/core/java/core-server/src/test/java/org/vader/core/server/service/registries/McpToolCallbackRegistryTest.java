package org.vader.core.server.service.registries;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
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

    @SuppressWarnings("unchecked")
    private void wireProviders(final ToolCallbackProvider... providers) {
        var objectProvider = mock(ObjectProvider.class);
        when(objectProvider.stream()).thenAnswer(invocation -> List.of(providers).stream());
        this.registry = new McpToolCallbackRegistry();
        ReflectionTestUtils.setField(this.registry, "toolCallbackProviders", objectProvider);
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
}
