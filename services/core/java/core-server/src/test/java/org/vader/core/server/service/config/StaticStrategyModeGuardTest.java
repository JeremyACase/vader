package org.vader.core.server.service.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.core.server.models.VaderMode;

class StaticStrategyModeGuardTest {

    private static StaticStrategyModeGuard guardIn(final VaderMode mode) {
        var guard = new StaticStrategyModeGuard();
        ReflectionTestUtils.setField(guard, "mode", mode);
        return guard;
    }

    @Test
    void requireTestMode_inDev_refusesToStart() {
        assertThatThrownBy(() -> guardIn(VaderMode.DEV).requireTestMode())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("vader.mode=TEST")
            .hasMessageContaining("DEV");
    }

    @Test
    void requireTestMode_inProd_refusesToStart() {
        assertThatThrownBy(() -> guardIn(VaderMode.PROD).requireTestMode())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("vader.mode=TEST")
            .hasMessageContaining("PROD");
    }

    @Test
    void requireTestMode_inTest_allowsStartup() {
        assertThatCode(() -> guardIn(VaderMode.TEST).requireTestMode())
            .doesNotThrowAnyException();
    }
}
