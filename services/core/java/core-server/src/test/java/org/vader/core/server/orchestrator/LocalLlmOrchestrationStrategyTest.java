package org.vader.core.server.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.vader.common.model.vader.dto.ClientPrompt;

class LocalLlmOrchestrationStrategyTest {

    private static final String BASE_URL = "http://vader-ollama:11434";
    private static final String MODEL = "deepseek-r1:1.5b";

    private RestTemplate restTemplate;
    private RestTemplateBuilder restTemplateBuilder;

    @BeforeEach
    void setUp() {
        this.restTemplate = mock(RestTemplate.class);
        this.restTemplateBuilder = mock(RestTemplateBuilder.class);
        when(this.restTemplateBuilder.build()).thenReturn(this.restTemplate);
    }

    @Test
    void orchestrate_withNoModelConfigured_throwsIllegalStateException() {
        var strategy = new LocalLlmOrchestrationStrategy(this.restTemplateBuilder, BASE_URL, "");
        var clientPrompt = new ClientPrompt();
        clientPrompt.setText("What's the weather like?");

        assertThatThrownBy(() -> strategy.orchestrate(clientPrompt))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No Ollama model is configured");
    }

    @Test
    void orchestrate_withNullModelConfigured_throwsIllegalStateException() {
        var strategy = new LocalLlmOrchestrationStrategy(this.restTemplateBuilder, BASE_URL, null);
        var clientPrompt = new ClientPrompt();
        clientPrompt.setText("What's the weather like?");

        assertThatThrownBy(() -> strategy.orchestrate(clientPrompt))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No Ollama model is configured");
    }

    @Test
    @SuppressWarnings("unchecked")
    void orchestrate_withModelConfigured_postsToOllamaAndReturnsResponseText() {
        var clientPrompt = new ClientPrompt();
        clientPrompt.setText("What's the weather like?");

        Map<String, Object> responseBody = Map.of("response", "It's sunny.");
        when(this.restTemplate.exchange(
            eq(BASE_URL + "/api/generate"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)))
            .thenReturn(ResponseEntity.ok(responseBody));

        var strategy = new LocalLlmOrchestrationStrategy(this.restTemplateBuilder, BASE_URL, MODEL);
        String result = strategy.orchestrate(clientPrompt);

        assertThat(result).isEqualTo("It's sunny.");

        ArgumentCaptor<HttpEntity<Map<String, Object>>> requestCaptor =
            ArgumentCaptor.forClass(HttpEntity.class);
        verify(this.restTemplate).exchange(
            eq(BASE_URL + "/api/generate"),
            eq(HttpMethod.POST),
            requestCaptor.capture(),
            any(ParameterizedTypeReference.class));

        Map<String, Object> sentBody = requestCaptor.getValue().getBody();
        assertThat(sentBody).containsEntry("model", MODEL);
        assertThat(sentBody).containsEntry("prompt", "What's the weather like?");
        assertThat(sentBody).containsEntry("stream", false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void orchestrate_withNullResponseBody_returnsNull() {
        var clientPrompt = new ClientPrompt();
        clientPrompt.setText("What's the weather like?");

        when(this.restTemplate.exchange(
            eq(BASE_URL + "/api/generate"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)))
            .thenReturn(ResponseEntity.ok(null));

        var strategy = new LocalLlmOrchestrationStrategy(this.restTemplateBuilder, BASE_URL, MODEL);

        assertThat(strategy.orchestrate(clientPrompt)).isNull();
    }
}
