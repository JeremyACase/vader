package org.vader.core.server.service.config.orchestrator;

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
 * Gives every Ollama HTTP call a bounded read timeout.
 *
 * <p>Spring AI's {@code OllamaApiAutoConfiguration} builds its {@code OllamaApi} from whichever
 * {@link RestClient.Builder} bean is available in the context (an {@code ObjectProvider}, exactly
 * so callers can customize it this way) -- and, absent one, falls back to a completely
 * unconfigured {@code RestClient.builder()}. That default has no read timeout at all: the JDK
 * {@link HttpClient} Spring auto-detects here (no Apache/Jetty/Reactor Netty client is on the
 * classpath, same as {@code SandboxExecutionClientConfig}) blocks on a plain, untimed
 * {@code CompletableFuture.get()} deep inside {@code JdkClientHttpRequest} -- confirmed directly
 * against a running replica via {@code jstack}, mid-incident, rather than assumed. A single slow
 * or hung generation therefore parks the entire single-worker {@code LlmRequestQueue} forever:
 * every other call (task inference turns, evaluation, reattempt decisions, even later
 * decompositions) queues up behind it and is eventually reported to its own caller as "unreachable"
 * once its patience runs out, while the one stuck call itself never actually clears -- the queue
 * stays wedged until something outside the JVM (an operator restarting the Ollama pod) breaks the
 * connection. Giving the request itself a bound means it fails cleanly and frees the worker for
 * the next queued call instead.</p>
 *
 * <p>{@code @Primary}: Spring Boot's own {@code RestClientAutoConfiguration} also registers an
 * unqualified {@code RestClient.Builder} bean, and {@code OllamaApiAutoConfiguration} asks for
 * one by type with no qualifier -- without {@code @Primary} here, two equally-eligible candidates
 * would make that lookup ambiguous and Spring AI would silently fall back to its own unconfigured
 * default, defeating this entirely. If this application ever grows another, unrelated consumer of
 * the plain autoconfigured builder, it will also see this timeout; there is no narrower official
 * hook to target only Ollama.</p>
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
