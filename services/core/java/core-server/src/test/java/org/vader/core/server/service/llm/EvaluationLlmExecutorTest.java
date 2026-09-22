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
import org.vader.common.model.vader.entity.TaskAttemptStatus;
import org.vader.core.server.models.EvaluationRequest;

class EvaluationLlmExecutorTest {

    private static final EvaluationRequest REQUEST = new EvaluationRequest(
        "title", "description", TaskAttemptStatus.SUCCEEDED, "the result", null, List.of());

    private static ChatResponse responseWith(final String assistantText) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(assistantText))));
    }

    private static EvaluationLlmExecutor executor(final ChatClient.Builder builder) {
        var executor = new EvaluationLlmExecutor();
        ReflectionTestUtils.setField(executor, "chatClientBuilder", builder);
        return executor;
    }

    @Test
    void execute_returnsTheParsedVerdict() {
        var chatModel = mock(ChatModel.class);
        when(chatModel.getDefaultOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenReturn(
            responseWith("{\"passed\":true,\"reasoning\":\"looks correct\"}"));

        var outcome = executor(ChatClient.builder(chatModel)).execute(REQUEST);

        assertThat(outcome.isUnreachable()).isFalse();
        assertThat(outcome.verdict().passed()).isTrue();
        assertThat(outcome.verdict().reasoning()).isEqualTo("looks correct");
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
        assertThat(outcome.verdict()).isNull();
    }
}
