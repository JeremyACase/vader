package org.vader.core.server.llm;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Gives every Ollama HTTP call a bounded read timeout, so a hung generation fails and frees the
 * single-worker {@code LlmRequestQueue} instead of blocking every queued call behind it. Without
 * this bean Spring AI falls back to a {@code RestClient} with no read timeout at all.
 *
 * <p>{@code @Primary} because Spring Boot also registers an unqualified {@code RestClient.Builder},
 * and Spring AI's Ollama autoconfiguration looks one up by type -- an ambiguous lookup makes it
 * silently use its own untimed default. Any other consumer of the autoconfigured builder gets
 * this timeout too; there is no narrower hook.</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "vader.orchestrator", name = "type", havingValue = "local")
public class OllamaClientConfig {

    @Value("${vader.orchestrator.local.request-timeout-seconds:300}")
    private long requestTimeoutSeconds;

    /**
     * Builds the timeout-bounded client Spring AI's Ollama autoconfiguration picks up.
     *
     * @return the configured builder
     */
    @Bean
    @Primary
    public RestClient.Builder ollamaRestClientBuilder() {
        var requestFactory = new JdkClientHttpRequestFactory(HttpClient.newHttpClient());
        requestFactory.setReadTimeout(Duration.ofSeconds(this.requestTimeoutSeconds));
        return RestClient.builder().requestFactory(requestFactory);
    }
}
