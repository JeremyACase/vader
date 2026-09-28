package org.vader.core.server.service.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.retry.support.RetryTemplate;

/**
 * Pins the Spring AI behavior the Helm-injected output cap depends on: the cap is set once, as the
 * Ollama chat model's default {@code num-predict} option, and must survive being merged with the
 * per-call options an executor passes (e.g. {@code InferenceTurnLlmExecutor}'s tool-calling
 * options, which say nothing about output length). If a Spring AI upgrade ever stopped merging
 * defaults this way, the cap would silently vanish and a runaway generation could again hold
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
            .defaultOptions(OllamaOptions.builder()
                .model("any-model")
                .numPredict(MAX_OUTPUT_TOKENS)
                .build())
            .retryTemplate(RetryTemplate.builder().maxAttempts(1).build())
            .build();
        var perCallOptions = ToolCallingChatOptions.builder()
            .toolCallbacks(List.of())
            .internalToolExecutionEnabled(false)
            .build();

        assertThatThrownBy(() -> chatModel.call(
            new Prompt(List.of(new UserMessage("hi")), perCallOptions)))
            .hasMessageContaining("request captured");

        var requestCaptor = ArgumentCaptor.forClass(OllamaApi.ChatRequest.class);
        verify(ollamaApi).chat(requestCaptor.capture());
        assertThat(requestCaptor.getValue().options())
            .containsEntry("num_predict", MAX_OUTPUT_TOKENS);
    }
}
