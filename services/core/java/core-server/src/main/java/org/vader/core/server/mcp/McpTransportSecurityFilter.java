package org.vader.core.server.mcp;

import io.modelcontextprotocol.server.transport.ServerTransportSecurityException;
import io.modelcontextprotocol.server.transport.ServerTransportSecurityValidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects MCP transport requests whose {@code Host} or {@code Origin} header is not allowlisted,
 * so a web page cannot reach this unauthenticated MCP server through a victim's browser by DNS
 * rebinding.
 *
 * <p>The MCP SDK ships this check as {@link ServerTransportSecurityValidator}, but Spring AI's
 * transport auto-configuration installs a no-op one. Running the SDK's validator as a filter on
 * the MCP endpoints applies it to whichever transport is configured.</p>
 */
public class McpTransportSecurityFilter extends OncePerRequestFilter {

    private final ServerTransportSecurityValidator validator;

    /**
     * Creates the filter.
     *
     * @param validator the header check every MCP request must pass
     */
    public McpTransportSecurityFilter(final ServerTransportSecurityValidator validator) {
        this.validator = validator;
    }

    @Override
    protected void doFilterInternal(
            final HttpServletRequest request, final HttpServletResponse response,
            final FilterChain chain) throws ServletException, IOException {
        try {
            this.validator.validateHeaders(headersOf(request));
            chain.doFilter(request, response);
        } catch (ServerTransportSecurityException e) {
            response.sendError(e.getStatusCode(), e.getMessage());
        }
    }

    private static Map<String, List<String>> headersOf(final HttpServletRequest request) {
        return Collections.list(request.getHeaderNames()).stream()
            .collect(Collectors.toMap(
                Function.identity(),
                name -> Collections.list(request.getHeaders(name)),
                (first, second) -> first));
    }
}
