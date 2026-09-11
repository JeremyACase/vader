package org.vader.core.server.service.backpressure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.core.server.service.registries.BackpressureRegistry;

class BackpressureSamplerTest {

    private static final String MODEL_TYPE = "ClientPrompt";

    private BackpressureRegistry registry;
    private InterfaceQueueBackpressure queue;
    private BackpressureSampler sampler;

    @BeforeEach
    void setUp() {
        this.registry = mock(BackpressureRegistry.class);
        this.queue = mock(InterfaceQueueBackpressure.class);
        when(this.registry.names()).thenReturn(Set.of(MODEL_TYPE));
        when(this.registry.require(MODEL_TYPE)).thenReturn(this.queue);

        this.sampler = new BackpressureSampler();
        ReflectionTestUtils.setField(this.sampler, "registry", this.registry);
        ReflectionTestUtils.setField(this.sampler, "sampleIntervalMs", 30_000L);
    }

    @Test
    void ratePerMinuteFor_isZeroBeforeTwoSamplesExist() {
        assertThat(this.sampler.ratePerMinuteFor(MODEL_TYPE)).isZero();

        when(this.queue.queuedRecordCount()).thenReturn(5L);
        this.sampler.sample();

        assertThat(this.sampler.ratePerMinuteFor(MODEL_TYPE)).isZero();
    }

    @Test
    void ratePerMinuteFor_scalesTheDeltaBetweenTheLastTwoSamplesToPerMinute() {
        when(this.queue.queuedRecordCount()).thenReturn(5L, 8L);

        this.sampler.sample();
        this.sampler.sample();

        // (8 - 5) delta over a 30s interval -> 6 per minute
        assertThat(this.sampler.ratePerMinuteFor(MODEL_TYPE)).isEqualTo(6f);
    }

    @Test
    void ratePerMinuteFor_reportsNegativeRateWhenBacklogDraining() {
        when(this.queue.queuedRecordCount()).thenReturn(10L, 9L, 4L);

        this.sampler.sample();
        this.sampler.sample();
        this.sampler.sample();

        // window keeps only the last two: (4 - 9) over 30s -> -10 per minute
        assertThat(this.sampler.ratePerMinuteFor(MODEL_TYPE)).isEqualTo(-10f);
    }

    @Test
    void ratePerMinuteFor_isZeroForAnUnsampledModelType() {
        assertThat(this.sampler.ratePerMinuteFor("Unknown")).isZero();
    }
}
