package org.vader.core.server.service.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.vader.core.server.models.ReattemptDecisionRequest;

class ReattemptDecisionLlmExecutorTest {

    private static final ReattemptDecisionRequest REQUEST = new ReattemptDecisionRequest(
        "title", "description", 1, 3, "it broke", List.of());

    private static ChatResponse responseWith(final String assistantText) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(assistantText))));
    }

    private static ReattemptDecisionLlmExecutor executor(final ChatClient.Builder builder) {
        var executor = new ReattemptDecisionLlmExecutor();
        ReflectionTestUtils.setField(executor, "chatClientBuilder", builder);
        return executor;
    }

    @Test
    void execute_returnsTheParsedDecision() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.getDefaultOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenReturn(
            responseWith("{\"shouldReattempt\":false,\"reasoning\":\"won't help\"}"));

        var outcome = executor(ChatClient.builder(chatModel)).execute(REQUEST);

        assertThat(outcome.isUnreachable()).isFalse();
        assertThat(outcome.decision().shouldReattempt()).isFalse();
        assertThat(outcome.decision().reasoning()).isEqualTo("won't help");
    }

    @Test
    void execute_whenTheModelIsUnreachable_returnsAnUnreachableOutcomeInsteadOfThrowing() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.getDefaultOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class)))
            .thenThrow(new ResourceAccessException("connection refused"));

        var outcome = executor(ChatClient.builder(chatModel)).execute(REQUEST);

        assertThat(outcome.isUnreachable()).isTrue();
        assertThat(outcome.unreachableReason()).contains("connection refused");
        assertThat(outcome.decision()).isNull();
    }
}
