package org.vader.core.server.mcp;

import io.modelcontextprotocol.server.transport.DefaultServerTransportSecurityValidator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link McpTransportSecurityFilter} on every MCP transport endpoint (SSE, its message
 * endpoint, and streamable HTTP), whichever protocol is configured.
 *
 * <p>The host allowlist must never be empty: the SDK's validator skips the {@code Host} check
 * entirely when it is, and the {@code Host} check is what stops DNS rebinding, since a rebound
 * page's same-origin requests need not carry an {@code Origin} header at all.</p>
 */
@Configuration
public class McpTransportSecurityConfig {

    @Value("${vader.mcp.transport-security.allowed-hosts:localhost,localhost:*,127.0.0.1,"
        + "127.0.0.1:*}")
    private List<String> allowedHosts;

    @Value("${vader.mcp.transport-security.allowed-origins:http://localhost:*,"
        + "http://127.0.0.1:*}")
    private List<String> allowedOrigins;

    @Value("${spring.ai.mcp.server.sse-endpoint:/sse}")
    private String sseEndpoint;

    @Value("${spring.ai.mcp.server.sse-message-endpoint:/mcp/message}")
    private String sseMessageEndpoint;

    @Value("${spring.ai.mcp.server.streamable-http.mcp-endpoint:/mcp}")
    private String streamableEndpoint;

    /**
     * Builds the filter registration.
     *
     * @return the filter, mapped to the MCP endpoints only
     * @throws IllegalStateException if the host allowlist is empty
     */
    @Bean
    public FilterRegistrationBean<McpTransportSecurityFilter> mcpTransportSecurityFilter() {
        if (this.allowedHosts.isEmpty()) {
            throw new IllegalStateException(
                "vader.mcp.transport-security.allowed-hosts must not be empty: an empty list "
                    + "disables the Host check that blocks DNS rebinding");
        }
        var validator = DefaultServerTransportSecurityValidator.builder()
            .allowedHosts(this.allowedHosts)
            .allowedOrigins(this.allowedOrigins)
            .build();
        var registration = new FilterRegistrationBean<>(new McpTransportSecurityFilter(validator));
        registration.setUrlPatterns(List.of(
            this.sseEndpoint, this.sseMessageEndpoint, this.streamableEndpoint));
        return registration;
    }
}
