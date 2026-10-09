package org.vader.core.server.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SecurityHeadersFilterTest {

    @Test
    void doFilter_addsTheHardeningHeadersAndContinuesTheChain() throws Exception {
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        new SecurityHeadersFilter().doFilter(new MockHttpServletRequest(), response, chain);

        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("Cross-Origin-Resource-Policy")).isEqualTo("same-origin");
        assertThat(chain.getRequest()).isNotNull();
    }
}
