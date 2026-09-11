package org.vader.core.server.service.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables the {@code @Scheduled} pollers (inbox drain, back pressure sampler).
 *
 * <p>Gated on {@code vader.scheduling.enabled} (default true) so tests can disable the background
 * pollers and drive {@code drain()} / sampling deterministically.</p>
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "vader.scheduling.enabled", matchIfMissing = true)
public class SchedulingConfig {
}
