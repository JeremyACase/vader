package org.vader.core.server.web;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers open-session-in-view for every request except the agent-harness endpoints, in place
 * of Spring Boot's own registration, which backs off once this interceptor bean exists.
 *
 * <p>Open-session-in-view holds a request's JDBC connection from its first query until the
 * response is written, and an agent endpoint spends most of its request blocked on the LLM queue
 * or a sandbox. Excluded, each of those requests holds a connection only inside its own short
 * transactions. Every agent endpoint does all of its entity access inside a transaction, so none
 * relies on lazy loading after one ends.</p>
 *
 * <p>The {@link WebMvcConfigurer} is a bean rather than this class's own type so that
 * {@code @WebMvcTest} slices, which pick up every {@code WebMvcConfigurer} class but have no JPA,
 * leave this configuration out.</p>
 */
@Configuration
public class OpenEntityManagerInViewConfig {

    private static final String AGENT_PATH_PATTERN = "/vader/core-server/agent/**";

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    /**
     * The interceptor itself; defining it suppresses Spring Boot's path-unrestricted one.
     *
     * @return the open-session-in-view interceptor
     */
    @Bean
    public OpenEntityManagerInViewInterceptor openEntityManagerInViewInterceptor() {
        var interceptor = new OpenEntityManagerInViewInterceptor();
        interceptor.setEntityManagerFactory(this.entityManagerFactory);
        return interceptor;
    }

    /**
     * Registers the interceptor on every path but the agent endpoints.
     *
     * @return the registering configurer
     */
    @Bean
    public WebMvcConfigurer openEntityManagerInViewConfigurer() {
        var interceptor = this.openEntityManagerInViewInterceptor();
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(final InterceptorRegistry registry) {
                registry.addWebRequestInterceptor(interceptor)
                    .excludePathPatterns(AGENT_PATH_PATTERN);
            }
        };
    }
}
