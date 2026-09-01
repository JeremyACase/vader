package org.vader.core.server.orchestrator;

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
import org.springframework.web.client.RestTemplate;
import org.vader.common.model.vader.dto.ClientPrompt;
import org.vader.core.server.orchestrator.interfaces.InterfaceLlmOrchestrationStrategy;

/**
 * Coordinates RESTful traffic to/from a local Ollama instance.
 *
 * <p>Active only when {@code vader.orchestrator.type} is set to {@code local}, in which case the
 * Helm chart also installs an Ollama deployment for this strategy to talk to. Which model Ollama
 * is asked to run is not yet configurable; that is a follow-up feature.</p>
 */
@Component
@ConditionalOnProperty(prefix = "vader.orchestrator", name = "type", havingValue = "local")
public class LocalLlmOrchestrationStrategy implements InterfaceLlmOrchestrationStrategy {

    private static final Logger logger =
        LoggerFactory.getLogger(LocalLlmOrchestrationStrategy.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String model;

    /**
     * Constructs the strategy.
     *
     * @param restTemplateBuilder builder used to construct the {@link RestTemplate}
     * @param baseUrl the base URL of the local Ollama instance
     * @param model the Ollama model to prompt, if configured
     */
    public LocalLlmOrchestrationStrategy(
        RestTemplateBuilder restTemplateBuilder,
        @Value("${vader.orchestrator.local.base-url}") String baseUrl,
        @Value("${vader.orchestrator.local.model:}") String model) {
        this.restTemplate = restTemplateBuilder.build();
        this.baseUrl = baseUrl;
        this.model = model;
    }

    @Override
    public String orchestrate(ClientPrompt clientPrompt) {
        if (this.model == null || this.model.isBlank()) {
            throw new IllegalStateException(
                "No Ollama model is configured; model selection is not yet implemented.");
        }

        logger.info("Sending prompt to local Ollama instance at {}", this.baseUrl);

        Map<String, Object> request = Map.of(
            "model", this.model,
            "prompt", clientPrompt.getText(),
            "stream", false);

        ParameterizedTypeReference<Map<String, Object>> responseType =
            new ParameterizedTypeReference<>() {};
        var response = this.restTemplate.exchange(
            this.baseUrl + "/api/generate",
            HttpMethod.POST,
            new HttpEntity<>(request),
            responseType);

        Map<String, Object> body = response.getBody();
        return body == null ? null : String.valueOf(body.get("response"));
    }
}
