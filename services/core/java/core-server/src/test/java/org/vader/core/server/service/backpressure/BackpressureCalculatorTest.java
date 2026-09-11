package org.vader.core.server.service.backpressure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class BackpressureCalculatorTest {

    private static final String MODEL_TYPE = "ClientPrompt";

    private BackpressureRegistry registry;
    private BackpressureSampler sampler;
    private InterfaceQueueBackpressure queue;
    private BackpressureCalculator calculator;

    @BeforeEach
    void setUp() {
        this.registry = mock(BackpressureRegistry.class);
        this.sampler = mock(BackpressureSampler.class);
        this.queue = mock(InterfaceQueueBackpressure.class);
        this.calculator = new BackpressureCalculator();
        ReflectionTestUtils.setField(this.calculator, "registry", this.registry);
        ReflectionTestUtils.setField(this.calculator, "sampler", this.sampler);
    }

    @Test
    void calculateFor_combinesLiveCountersWithTheSampledRate() {
        when(this.registry.require(MODEL_TYPE)).thenReturn(this.queue);
        when(this.queue.queuedRecordCount()).thenReturn(4L);
        when(this.queue.openMessageCount()).thenReturn(1L);
        when(this.queue.maxOpenMessages()).thenReturn(3);
        when(this.sampler.ratePerMinuteFor(MODEL_TYPE)).thenReturn(2.5f);

        var backPressure = this.calculator.calculateFor(MODEL_TYPE);

        assertThat(backPressure.getIngress().getQueuedRecords()).isEqualTo(4L);
        assertThat(backPressure.getIngress().getQueueRatePerMinute()).isEqualTo(2.5f);
        assertThat(backPressure.getEgress().getMaxOpenMessages()).isEqualTo(3);
        assertThat(backPressure.getEgress().getCurrentOpenMessages()).isEqualTo(1);
    }

    @Test
    void calculateFor_propagatesTheRegistryFailureForAnUnknownModelType() {
        when(this.registry.require("Nope"))
            .thenThrow(new IllegalArgumentException("no queue for Nope"));

        assertThatThrownBy(() -> this.calculator.calculateFor("Nope"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
