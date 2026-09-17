package org.vader.core.server.service.decorators.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

class LoggingToolCallbackDecoratorTest {

    private ToolCallback delegate;
    private ToolDefinition toolDefinition;
    private LoggingToolCallbackDecorator decorator;

    @BeforeEach
    void setUp() {
        this.delegate = mock(ToolCallback.class);
        this.toolDefinition = mock(ToolDefinition.class);
        when(this.toolDefinition.name()).thenReturn("run_python_code");
        when(this.delegate.getToolDefinition()).thenReturn(this.toolDefinition);
        this.decorator = new LoggingToolCallbackDecorator(this.delegate);
    }

    @Test
    void getToolDefinition_delegatesToTheWrappedCallback() {
        assertThat(this.decorator.getToolDefinition()).isSameAs(this.toolDefinition);
    }

    @Test
    void getToolMetadata_delegatesToTheWrappedCallback() {
        var metadata = mock(ToolMetadata.class);
        when(this.delegate.getToolMetadata()).thenReturn(metadata);

        assertThat(this.decorator.getToolMetadata()).isSameAs(metadata);
    }

    @Test
    void call_withOneArgument_delegatesAndReturnsTheResult() {
        when(this.delegate.call("{\"name\":\"sandbox-1\"}")).thenReturn("{\"stdout\":\"ok\"}");

        var result = this.decorator.call("{\"name\":\"sandbox-1\"}");

        assertThat(result).isEqualTo("{\"stdout\":\"ok\"}");
        verify(this.delegate).call("{\"name\":\"sandbox-1\"}");
    }

    @Test
    void call_withToolContext_delegatesToTheTwoArgumentOverload() {
        var context = mock(ToolContext.class);
        when(this.delegate.call("{}", context)).thenReturn("{\"ok\":true}");

        var result = this.decorator.call("{}", context);

        assertThat(result).isEqualTo("{\"ok\":true}");
        verify(this.delegate).call("{}", context);
    }

    @Test
    void call_whenTheDelegateThrows_propagatesTheException() {
        when(this.delegate.call("{}")).thenThrow(new IllegalStateException("sandbox not found"));

        assertThatThrownBy(() -> this.decorator.call("{}"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("sandbox not found");
    }
}
