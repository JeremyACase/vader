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
import org.vader.core.exceptions.OrchestratorUnavailableException;

class LocalInferenceGatewayStrategyTest {

    private ChatModel chatModel;

    @BeforeEach
    void setUp() {
        this.chatModel = mock(ChatModel.class);
        when(this.chatModel.getDefaultOptions())
            .thenReturn(ToolCallingChatOptions.builder().build());
    }

    private LocalInferenceGatewayStrategy strategy(final ChatClient.Builder builder) {
        var strategy = new LocalInferenceGatewayStrategy();
        ReflectionTestUtils.setField(strategy, "chatClientBuilder", builder);
        return strategy;
    }

    private static ChatResponse responseWith(final String assistantText) {
        var metadata = ChatResponseMetadata.builder()
            .usage(new DefaultUsage(3, 4, 7))
            .build();
        return new ChatResponse(
            List.of(new Generation(new AssistantMessage(assistantText))), metadata);
    }

    @Test
    void complete_returnsContentAndTokensSpent() {
        when(this.chatModel.call(any(Prompt.class))).thenReturn(responseWith("hello there"));

        var turn = this.strategy(ChatClient.builder(this.chatModel)).complete("hi");

        assertThat(turn.content()).isEqualTo("hello there");
        assertThat(turn.tokensSpent()).isEqualTo(7L);
    }

    @Test
    void complete_whenModelUnreachable_throwsOrchestratorUnavailable() {
        when(this.chatModel.call(any(Prompt.class)))
            .thenThrow(new ResourceAccessException("connection refused"));

        assertThatThrownBy(() -> this.strategy(ChatClient.builder(this.chatModel)).complete("hi"))
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

        strategy.complete("first");
        strategy.complete("second");

        verify(builder, times(2)).build();
    }
}
