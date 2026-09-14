package org.vader.core.server.service.config.pythonsandbox;

import java.net.http.HttpClient;
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

    /**
     * Builds the client pinned to HTTP/1.1.
     *
     * <p>The JDK's {@link HttpClient} -- the request factory Spring Boot auto-detects when no
     * Apache/Jetty/Reactor Netty client is on the classpath, as is the case here -- attempts an
     * HTTP/2 cleartext (h2c) upgrade by default. Uvicorn (the ASGI server
     * {@code core-python-sandbox-server} runs on) only speaks HTTP/1.1 and rejects that upgrade
     * attempt with "Unsupported upgrade request", silently dropping the request body and making
     * every call fail with a 422 for a "missing" body. Pinning the version here avoids the
     * upgrade attempt entirely.</p>
     *
     * @return the configured client
     */
    @Bean
    public RestClient sandboxExecutionRestClient() {
        var httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        return RestClient.builder()
            .requestFactory(new JdkClientHttpRequestFactory(httpClient))
            .build();
    }
}
