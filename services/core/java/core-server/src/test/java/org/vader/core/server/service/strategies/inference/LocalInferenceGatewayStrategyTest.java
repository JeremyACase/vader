package org.vader.core.server.service.strategies.inference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.vader.core.server.exceptions.OrchestratorUnavailableException;
import org.vader.core.server.models.ConversationMessage;
import org.vader.core.server.models.ConversationRole;
import org.vader.core.server.service.registries.McpToolCallbackRegistry;

class LocalInferenceGatewayStrategyTest {

    private ChatModel chatModel;
    private McpToolCallbackRegistry toolCallbackRegistry;

    @BeforeEach
    void setUp() {
        this.chatModel = mock(ChatModel.class);
        when(this.chatModel.getDefaultOptions())
            .thenReturn(ToolCallingChatOptions.builder().build());
        this.toolCallbackRegistry = mock(McpToolCallbackRegistry.class);
        when(this.toolCallbackRegistry.all()).thenReturn(List.of());
    }

    private LocalInferenceGatewayStrategy strategy(final ChatClient.Builder builder) {
        var strategy = new LocalInferenceGatewayStrategy();
        ReflectionTestUtils.setField(strategy, "chatClientBuilder", builder);
        ReflectionTestUtils.setField(strategy, "toolCallbackRegistry", this.toolCallbackRegistry);
        return strategy;
    }

    private static List<ConversationMessage> userTurn(final String text) {
        return List.of(new ConversationMessage(ConversationRole.USER, text, null, null, null));
    }

    private static ChatResponse responseWith(final String assistantText) {
        var metadata = ChatResponseMetadata.builder()
            .usage(new DefaultUsage(3, 4, 7))
            .build();
        return new ChatResponse(
            List.of(new Generation(new AssistantMessage(assistantText))), metadata);
    }

    private static ChatResponse responseWithToolCall(
            final String id, final String name, final String argumentsJson) {
        var metadata = ChatResponseMetadata.builder()
            .usage(new DefaultUsage(3, 4, 7))
            .build();
        var toolCall = new AssistantMessage.ToolCall(id, "function", name, argumentsJson);
        var assistantMessage = new AssistantMessage(null, java.util.Map.of(), List.of(toolCall));
        return new ChatResponse(List.of(new Generation(assistantMessage)), metadata);
    }

    @Test
    void complete_returnsContentAndTokensSpent() {
        when(this.chatModel.call(any(Prompt.class))).thenReturn(responseWith("hello there"));

        var turn = this.strategy(ChatClient.builder(this.chatModel)).complete(userTurn("hi"));

        assertThat(turn.content()).isEqualTo("hello there");
        assertThat(turn.toolCalls()).isEmpty();
        assertThat(turn.tokensSpent()).isEqualTo(7L);
    }

    @Test
    void complete_whenTheModelRequestsTool_returnsTheRequestInsteadOfExecutingIt() {
        when(this.chatModel.call(any(Prompt.class)))
            .thenReturn(responseWithToolCall("call-1", "get_object_content", "{\"id\":\"abc\"}"));

        var turn = this.strategy(ChatClient.builder(this.chatModel)).complete(userTurn("hi"));

        assertThat(turn.content()).isNull();
        assertThat(turn.toolCalls()).hasSize(1);
        var toolCall = turn.toolCalls().get(0);
        assertThat(toolCall.id()).isEqualTo("call-1");
        assertThat(toolCall.name()).isEqualTo("get_object_content");
        assertThat(toolCall.argumentsJson()).isEqualTo("{\"id\":\"abc\"}");
    }

    @Test
    void complete_whenModelUnreachable_throwsOrchestratorUnavailable() {
        when(this.chatModel.call(any(Prompt.class)))
            .thenThrow(new ResourceAccessException("connection refused"));

        assertThatThrownBy(
            () -> this.strategy(ChatClient.builder(this.chatModel)).complete(userTurn("hi")))
            .isInstanceOf(OrchestratorUnavailableException.class)
            .hasMessageContaining("local LLM");
    }

    /**
     * Regression test for spring-projects/spring-ai#3537: reusing one {@link ChatClient} across
     * calls corrupts its internal advisor-chain state under concurrency and intermittently fails
     * with {@code IllegalStateException: No CallAdvisors available to execute}. This asserts the
     * actual fix mechanism -- a fresh client built per call -- rather than trying to reproduce
     * the timing-dependent upstream race itself.
     */
    @Test
    void complete_buildsFreshChatClientPerCall() {
        when(this.chatModel.call(any(Prompt.class))).thenReturn(responseWith("ok"));
        var builder = spy(ChatClient.builder(this.chatModel));
        var strategy = this.strategy(builder);

        strategy.complete(userTurn("first"));
        strategy.complete(userTurn("second"));

        verify(builder, times(2)).build();
    }
}
