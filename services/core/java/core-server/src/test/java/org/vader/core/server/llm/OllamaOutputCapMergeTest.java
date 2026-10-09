package org.vader.core.server.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

/**
 * Pins the Spring AI behavior the Helm-injected output cap depends on: the cap is set once, as the
 * Ollama chat model's default {@code num-predict} option, and must survive the chat client
 * merging it with the per-call options an executor passes (e.g. {@code InferenceTurnLlmExecutor}'s
 * tool callbacks, which say nothing about output length). If a Spring AI upgrade ever stopped
 * merging defaults this way, the cap would silently vanish and a runaway generation could hold
 * Ollama's only slot indefinitely -- this test fails first instead.
 */
class OllamaOutputCapMergeTest {

    private static final int MAX_OUTPUT_TOKENS = 2048;

    @Test
    void theDefaultOutputCapSurvivesPerCallToolCallingOptions() {
        var ollamaApi = mock(OllamaApi.class);
        // Stop right after the request is built: the request itself is all this test cares about.
        when(ollamaApi.chat(any())).thenThrow(new IllegalStateException("request captured"));
        var chatModel = OllamaChatModel.builder()
            .ollamaApi(ollamaApi)
            .options(OllamaChatOptions.builder()
                .model("any-model")
                .numPredict(MAX_OUTPUT_TOKENS)
                .build())
            .retryTemplate(new RetryTemplate(RetryPolicy.withMaxRetries(0)))
            .build();

        assertThatThrownBy(() -> ChatClient.builder(chatModel).build().prompt()
            .messages(new UserMessage("hi"))
            .toolCallbacks(List.of())
            .call()
            .chatResponse())
            .hasStackTraceContaining("request captured");

        var requestCaptor = ArgumentCaptor.forClass(OllamaApi.ChatRequest.class);
        verify(ollamaApi).chat(requestCaptor.capture());
        assertThat(requestCaptor.getValue().options())
            .containsEntry("num_predict", MAX_OUTPUT_TOKENS);
    }
}
