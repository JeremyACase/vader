package org.vader.core.server.orchestrator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.vader.common.model.vader.dto.ClientPrompt;
import org.vader.core.server.orchestrator.interfaces.InterfaceLlmOrchestrationStrategy;

/**
 * Coordinates RESTful traffic to/from a local Ollama instance.
 *
 * <p>Active only when {@code vader.orchestrator.type} is set to {@code local}, in which case the
 * Helm chart also installs an Ollama deployment for this strategy to talk to.</p>
 *
 * <p>The prompt is wrapped with decomposition instructions and the request pins Ollama's
 * structured-output {@code format} to the task-plan JSON schema, so the model is constrained to
 * return a single JSON object that the caller can parse and validate as a task plan.</p>
 */
@Component
@ConditionalOnProperty(prefix = "vader.orchestrator", name = "type", havingValue = "local")
public class LocalLlmOrchestrationStrategy implements InterfaceLlmOrchestrationStrategy {

    private static final Logger logger =
        LoggerFactory.getLogger(LocalLlmOrchestrationStrategy.class);

    private static final String DECOMPOSITION_INSTRUCTIONS = """
        You are Vader, a planning assistant. Decompose the user's problem into a concrete plan.
        Respond with a single JSON object and nothing else, of the form:
          {"objective": "<one sentence restating the goal>",
           "taskGraph": {"tasks": [{"title": "<short>", "description": "<what to do>"}]}}
        Break the objective into 2 to 6 top-level tasks. No commentary, no markdown.

        User problem:
        """;

    private static final String TASK_PLAN_JSON_SCHEMA = """
        {
          "type": "object",
          "properties": {
            "objective": {"type": "string"},
            "taskGraph": {
              "type": "object",
              "properties": {
                "tasks": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "title": {"type": "string"},
                      "description": {"type": "string"}
                    },
                    "required": ["title", "description"]
                  }
                }
              },
              "required": ["tasks"]
            }
          },
          "required": ["objective", "taskGraph"]
        }
        """;

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String model;
    private final Map<String, Object> taskPlanSchema;

    /**
     * Constructs the strategy.
     *
     * @param restTemplateBuilder builder used to construct the {@link RestTemplate}
     * @param objectMapper mapper used to parse the embedded task-plan JSON schema
     * @param baseUrl the base URL of the local Ollama instance
     * @param model the Ollama model to prompt, if configured
     */
    public LocalLlmOrchestrationStrategy(
        final RestTemplateBuilder restTemplateBuilder,
        final ObjectMapper objectMapper,
        @Value("${vader.orchestrator.local.base-url}") final String baseUrl,
        @Value("${vader.orchestrator.local.model:}") final String model) {

        this.restTemplate = restTemplateBuilder
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofMinutes(4))
            .build();
        this.baseUrl = baseUrl;
        this.model = model;
        try {
            this.taskPlanSchema = objectMapper.readValue(
                TASK_PLAN_JSON_SCHEMA, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Embedded task-plan JSON schema is invalid.", e);
        }
    }

    @Override
    public String orchestrate(final ClientPrompt clientPrompt) {
        if (this.model == null || this.model.isBlank()) {
            throw new IllegalStateException(
                "No Ollama model is configured; model selection is not yet implemented.");
        }

        logger.info("Requesting a problem decomposition from Ollama at {}", this.baseUrl);

        Map<String, Object> request = Map.of(
            "model", this.model,
            "prompt", DECOMPOSITION_INSTRUCTIONS + clientPrompt.getText(),
            "stream", false,
            "format", this.taskPlanSchema);

        ParameterizedTypeReference<Map<String, Object>> responseType =
            new ParameterizedTypeReference<>() {};
        try {
            var response = this.restTemplate.exchange(
                this.baseUrl + "/api/generate",
                HttpMethod.POST,
                new HttpEntity<>(request),
                responseType);

            Map<String, Object> body = response.getBody();
            return body == null ? null : String.valueOf(body.get("response"));
        } catch (RestClientException e) {
            throw new OrchestratorUnavailableException(
                "Could not reach the Ollama instance at " + this.baseUrl, e);
        }
    }
}
