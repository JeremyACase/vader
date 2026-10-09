package org.vader.core.server.mcp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Drives the real MCP message endpoint through the full filter chain, so the guard is proven to
 * sit in front of the transport Spring AI actually configures, not just to work in isolation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "vader.scheduling.enabled=false")
class McpTransportSecurityIntegrationTest {

    private static final String MESSAGE_ENDPOINT = "/mcp/message";

    private static final String PING = """
        {"jsonrpc": "2.0", "id": 1, "method": "ping"}
        """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void reboundHostNeverReachesTheTransport() throws Exception {
        this.mockMvc.perform(post(MESSAGE_ENDPOINT)
                .header("Host", "attacker.example:8080")
                .contentType(MediaType.APPLICATION_JSON)
                .content(PING))
            .andExpect(status().is(421));
    }

    @Test
    void foreignBrowserOriginNeverReachesTheTransport() throws Exception {
        this.mockMvc.perform(post(MESSAGE_ENDPOINT)
                .header("Host", "localhost:8080")
                .header("Origin", "http://attacker.example")
                .contentType(MediaType.APPLICATION_JSON)
                .content(PING))
            .andExpect(status().isForbidden());
    }

    @Test
    void localClientReachesTheTransport() throws Exception {
        // No session id was opened over /sse, so the transport itself answers 400 -- proof the
        // request got past the guard rather than being stopped by it.
        this.mockMvc.perform(post(MESSAGE_ENDPOINT)
                .header("Host", "localhost:8080")
                .contentType(MediaType.APPLICATION_JSON)
                .content(PING))
            .andExpect(status().isBadRequest());
    }
}
