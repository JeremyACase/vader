package org.vader.core.server.sandbox;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Builds the {@link RestClient} {@code SandboxExecutionClient} uses to call a Python sandbox
 * pod's own HTTP server.
 *
 * <p>Built separately from Spring Boot's shared auto-configured {@code RestClient.Builder} bean,
 * rather than customizing it, so this HTTP/1.1 pin only affects sandbox-execution calls if this
 * application ever grows another, unrelated use of {@code RestClient}.</p>
 */
@Configuration
public class SandboxExecutionClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    /**
     * Builds the client pinned to HTTP/1.1, with a connect timeout.
     *
     * <p>Pinned to HTTP/1.1 because the JDK {@link HttpClient} attempts an h2c upgrade by
     * default, which Uvicorn (the sandbox server's ASGI server) rejects, dropping the request
     * body.</p>
     *
     * <p>The connect timeout bounds only establishing the connection, never a code run, so an
     * unanswering sandbox address cannot hold a caller -- including the readiness poll in
     * {@code PythonSandboxService} -- for as long as the OS lets a connect hang.</p>
     *
     * @return the configured client
     */
    @Bean
    public RestClient sandboxExecutionRestClient() {
        var httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
        return RestClient.builder()
            .requestFactory(new JdkClientHttpRequestFactory(httpClient))
            .build();
    }
}
