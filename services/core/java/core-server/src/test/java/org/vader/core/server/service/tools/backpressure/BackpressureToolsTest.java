package org.vader.core.server.service.tools.backpressure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.model.vader.queue.BackPressure;
import org.vader.common.model.vader.queue.Egress;
import org.vader.common.model.vader.queue.Ingress;
import org.vader.core.server.service.backpressure.BackpressureCalculator;
import org.vader.core.server.service.registries.BackpressureRegistry;

class BackpressureToolsTest {

    private BackpressureRegistry registry;
    private BackpressureCalculator calculator;
    private BackpressureTools tools;

    @BeforeEach
    void setUp() {
        this.registry = mock(BackpressureRegistry.class);
        this.calculator = mock(BackpressureCalculator.class);
        this.tools = new BackpressureTools();
        ReflectionTestUtils.setField(this.tools, "registry", this.registry);
        ReflectionTestUtils.setField(this.tools, "calculator", this.calculator);
    }

    @Test
    void listBackpressureQueues_returnsTheRegisteredNames() {
        when(this.registry.names()).thenReturn(Set.of("ClientPrompt"));

        assertThat(this.tools.listBackpressureQueues()).containsExactly("ClientPrompt");
    }

    @Test
    void getBackpressure_returnsTheCalculatedSnapshot() {
        var snapshot = snapshot();
        when(this.calculator.calculateFor("ClientPrompt")).thenReturn(snapshot);

        assertThat(this.tools.getBackpressure("ClientPrompt")).isSameAs(snapshot);
    }

    @Test
    void getBackpressure_forAnUnknownModelType_returnsAnErrorMap() {
        when(this.calculator.calculateFor("Bogus"))
            .thenThrow(new IllegalArgumentException("no queue for 'Bogus'"));

        assertThat(this.tools.getBackpressure("Bogus"))
            .isEqualTo(Map.of("error", "no queue for 'Bogus'"));
    }

    private static BackPressure snapshot() {
        var ingress = new Ingress();
        ingress.setQueuedRecords(1L);
        ingress.setQueueRatePerMinute(0f);
        var egress = new Egress();
        egress.setMaxOpenMessages(1);
        egress.setCurrentOpenMessages(0);
        var backPressure = new BackPressure();
        backPressure.setIngress(ingress);
        backPressure.setEgress(egress);
        return backPressure;
    }
}
