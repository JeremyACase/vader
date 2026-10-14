package org.vader.core.server.taskagent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.core.server.taskagent.model.TurnUsage;

class PromptBudgetMonitorTest {

    // A 16384-token window less 2048 reserved for the reply leaves a 14336-token prompt budget.
    private static final int CONTEXT_TOKENS = 16384;

    private static final int MAX_OUTPUT_TOKENS = 2048;

    private PromptBudgetMonitor monitor;

    @BeforeEach
    void setUp() {
        this.monitor = new PromptBudgetMonitor();
        ReflectionTestUtils.setField(this.monitor, "contextTokens", CONTEXT_TOKENS);
        ReflectionTestUtils.setField(this.monitor, "maxOutputTokens", MAX_OUTPUT_TOKENS);
    }

    @Test
    void warnIfNearLimit_isQuietWellInsideTheBudget() {
        assertThat(this.monitor.warnIfNearLimit("attempt", 0, new TurnUsage(2000, 50, 8000)))
            .isFalse();
    }

    @Test
    void warnIfNearLimit_flagsReportedTokenCountNearTheBudget() {
        assertThat(this.monitor.warnIfNearLimit("attempt", 3, new TurnUsage(13000, 50, 0)))
            .isTrue();
    }

    @Test
    void warnIfNearLimit_flagsLargePromptEvenWhenTheProviderUnderreportsIt() {
        // A cached prefix can make the reported count tiny; the characters sent still show it.
        assertThat(this.monitor.warnIfNearLimit("attempt", 5, new TurnUsage(300, 50, 40000)))
            .isTrue();
    }

    @Test
    void warnIfNearLimit_neverFlagsWithoutConfiguredWindow() {
        ReflectionTestUtils.setField(this.monitor, "contextTokens", null);

        assertThat(this.monitor.warnIfNearLimit("attempt", 0, new TurnUsage(99999, 0, 999999)))
            .isFalse();
    }
}
