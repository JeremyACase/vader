package org.vader.core.server.service.config.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.vader.core.server.service.decorators.tools.LoggingToolCallbackDecorator;

class McpToolCallLoggingPostProcessorTest {

    private final McpToolCallLoggingPostProcessor postProcessor =
        new McpToolCallLoggingPostProcessor();

    @Test
    void postProcessAfterInitialization_leavesNonToolCallbackProviderBeansUnchanged() {
        var bean = new Object();

        var result = this.postProcessAfterInitialization(bean);

        assertThat(result).isSameAs(bean);
    }

    @Test
    void postProcessAfterInitialization_wrapsEveryCallbackFromToolProviderBean() {
        var toolDefinition = mock(ToolDefinition.class);
        when(toolDefinition.name()).thenReturn("run_python_code");
        var callback = mock(ToolCallback.class);
        when(callback.getToolDefinition()).thenReturn(toolDefinition);
        when(callback.call("{}")).thenReturn("ok");
        ToolCallbackProvider provider = () -> new ToolCallback[] {callback};

        var result = this.postProcessAfterInitialization(provider);

        assertThat(result).isInstanceOf(ToolCallbackProvider.class);
        var wrapped = ((ToolCallbackProvider) result).getToolCallbacks();
        assertThat(wrapped).hasSize(1);
        assertThat(wrapped[0]).isInstanceOf(LoggingToolCallbackDecorator.class);
        assertThat(wrapped[0].call("{}")).isEqualTo("ok");
        verify(callback).call("{}");
    }

    private Object postProcessAfterInitialization(final Object bean) {
        return this.postProcessor.postProcessAfterInitialization(bean, "someBean");
    }
}
