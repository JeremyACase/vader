package org.vader.core.server.service.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.vader.core.server.models.VaderMode;

/**
 * Refuses to let core-server start with {@code vader.orchestrator.type=static} outside
 * {@code vader.mode=TEST}.
 *
 * <p>Static mode answers every prompt with the same canned task plan and every evaluation,
 * reattempt, refinement, synthesis, and inference call with a canned result. That is exactly what
 * a deterministic devops test pipeline needs, and exactly what a real user must never see -- so a
 * misconfigured DEV or PROD deployment fails at startup, loudly, instead of quietly serving
 * fabricated results. Active only when static mode is selected, so it costs nothing otherwise.</p>
 */
@Component
@ConditionalOnProperty(prefix = "vader.orchestrator", name = "type", havingValue = "static")
public class StaticStrategyModeGuard {

    @Value("${vader.mode:PROD}")
    private VaderMode mode;

    /**
     * Fails startup unless running in {@link VaderMode#TEST}.
     *
     * @throws IllegalStateException if static mode was selected in any other mode
     */
    @PostConstruct
    void requireTestMode() {
        if (this.mode != VaderMode.TEST) {
            throw new IllegalStateException(
                "vader.orchestrator.type=static returns canned results and is only permitted when "
                    + "vader.mode=TEST, but vader.mode is " + this.mode + ". Use "
                    + "vader.orchestrator.type=local.");
        }
    }
}
