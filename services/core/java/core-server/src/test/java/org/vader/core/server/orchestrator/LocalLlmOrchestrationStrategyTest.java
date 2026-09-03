package org.vader.core.server.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.vader.common.model.vader.dto.ClientPrompt;

class LocalLlmOrchestrationStrategyTest {

    private static final String BASE_URL = "http://vader-ollama:11434";
    private static final String MODEL = "deepseek-r1:1.5b";

    private RestTemplate restTemplate;
    private RestTemplateBuilder restTemplateBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        this.restTemplate = mock(RestTemplate.class);
        this.restTemplateBuilder = mock(RestTemplateBuilder.class);
        when(this.restTemplateBuilder.connectTimeout(any())).thenReturn(this.restTemplateBuilder);
        when(this.restTemplateBuilder.readTimeout(any())).thenReturn(this.restTemplateBuilder);
        when(this.restTemplateBuilder.build()).thenReturn(this.restTemplate);
    }

    private LocalLlmOrchestrationStrategy strategy(final String model) {
        return new LocalLlmOrchestrationStrategy(
            this.restTemplateBuilder, this.objectMapper, BASE_URL, model);
    }

    @Test
    void orchestrate_withNoModelConfigured_throwsIllegalStateException() {
        var strategy = strategy("");
        var clientPrompt = new ClientPrompt();
        clientPrompt.setText("What's the weather like?");

        assertThatThrownBy(() -> strategy.orchestrate(clientPrompt))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No Ollama model is configured");
    }

    @Test
    void orchestrate_withNullModelConfigured_throwsIllegalStateException() {
        var strategy = strategy(null);
        var clientPrompt = new ClientPrompt();
        clientPrompt.setText("What's the weather like?");

        assertThatThrownBy(() -> strategy.orchestrate(clientPrompt))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No Ollama model is configured");
    }

    @Test
    @SuppressWarnings("unchecked")
    void orchestrate_withModelConfigured_postsDecompositionRequestAndReturnsResponseText() {
        var clientPrompt = new ClientPrompt();
        clientPrompt.setText("Plan a birthday party");

        Map<String, Object> responseBody = Map.of("response", "{\"objective\":\"x\"}");
        when(this.restTemplate.exchange(
            eq(BASE_URL + "/api/generate"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)))
            .thenReturn(ResponseEntity.ok(responseBody));

        String result = strategy(MODEL).orchestrate(clientPrompt);

        assertThat(result).isEqualTo("{\"objective\":\"x\"}");

        ArgumentCaptor<HttpEntity<Map<String, Object>>> requestCaptor =
            ArgumentCaptor.forClass(HttpEntity.class);
        verify(this.restTemplate).exchange(
            eq(BASE_URL + "/api/generate"),
            eq(HttpMethod.POST),
            requestCaptor.capture(),
            any(ParameterizedTypeReference.class));

        Map<String, Object> sentBody = requestCaptor.getValue().getBody();
        assertThat(sentBody).containsEntry("model", MODEL);
        assertThat(sentBody).containsEntry("stream", false);
        assertThat(sentBody).containsKey("format");
        assertThat(sentBody.get("prompt")).asString()
            .contains("Plan a birthday party")
            .contains("single JSON object");
    }

    @Test
    @SuppressWarnings("unchecked")
    void orchestrate_whenOllamaIsUnreachable_throwsOrchestratorUnavailable() {
        var clientPrompt = new ClientPrompt();
        clientPrompt.setText("Plan a birthday party");

        when(this.restTemplate.exchange(
            eq(BASE_URL + "/api/generate"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)))
            .thenThrow(new ResourceAccessException("connection refused"));

        assertThatThrownBy(() -> strategy(MODEL).orchestrate(clientPrompt))
            .isInstanceOf(OrchestratorUnavailableException.class)
            .hasMessageContaining(BASE_URL);
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

        assertThat(strategy(MODEL).orchestrate(clientPrompt)).isNull();
    }
}
