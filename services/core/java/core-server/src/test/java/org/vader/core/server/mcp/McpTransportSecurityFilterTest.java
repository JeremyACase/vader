package org.vader.core.server.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.server.transport.DefaultServerTransportSecurityValidator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class McpTransportSecurityFilterTest {

    private McpTransportSecurityFilter filter;

    @BeforeEach
    void setUp() {
        var validator = DefaultServerTransportSecurityValidator.builder()
            .allowedHosts(List.of("localhost:*"))
            .allowedOrigins(List.of("http://localhost:*"))
            .build();
        this.filter = new McpTransportSecurityFilter(validator);
    }

    private static MockHttpServletRequest requestWithHost(final String host) {
        var request = new MockHttpServletRequest("GET", "/sse");
        request.addHeader("Host", host);
        return request;
    }

    @Test
    void anAllowlistedHostWithNoOriginReachesTheTransport() throws Exception {
        var chain = new MockFilterChain();
        var response = new MockHttpServletResponse();

        this.filter.doFilter(requestWithHost("localhost:30080"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void reboundHostIsRejectedWithMisdirectedRequest() throws Exception {
        var chain = new MockFilterChain();
        var response = new MockHttpServletResponse();

        this.filter.doFilter(requestWithHost("attacker.example:30080"), response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(421);
    }

    @Test
    void foreignBrowserOriginIsRejectedEvenWithAnAllowlistedHost() throws Exception {
        var request = requestWithHost("localhost:30080");
        request.addHeader("Origin", "http://attacker.example");
        var chain = new MockFilterChain();
        var response = new MockHttpServletResponse();

        this.filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void anAllowlistedBrowserOriginReachesTheTransport() throws Exception {
        var request = requestWithHost("localhost:30080");
        request.addHeader("Origin", "http://localhost:6274");
        var chain = new MockFilterChain();

        this.filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }
}
