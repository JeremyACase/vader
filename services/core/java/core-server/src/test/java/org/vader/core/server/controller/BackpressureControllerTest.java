package org.vader.core.server.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.model.vader.queue.BackPressure;
import org.vader.common.model.vader.queue.Egress;
import org.vader.common.model.vader.queue.Ingress;
import org.vader.core.server.service.backpressure.BackpressureCalculator;
import org.vader.core.server.service.registries.BackpressureRegistry;

class BackpressureControllerTest {

    private BackpressureRegistry registry;
    private BackpressureCalculator calculator;
    private BackpressureController controller;

    @BeforeEach
    void setUp() {
        this.registry = mock(BackpressureRegistry.class);
        this.calculator = mock(BackpressureCalculator.class);
        this.controller = new BackpressureController();
        ReflectionTestUtils.setField(this.controller, "registry", this.registry);
        ReflectionTestUtils.setField(this.controller, "calculator", this.calculator);
    }

    @Test
    void queuedModelTypes_returnsTheRegisteredNames() {
        when(this.registry.names()).thenReturn(new LinkedHashSet<>(Set.of("ClientPrompt")));

        assertThat(this.controller.queuedModelTypes()).containsExactly("ClientPrompt");
    }

    @Test
    void backPressureFor_returnsTheCalculatedSnapshot() {
        var snapshot = snapshot();
        when(this.calculator.calculateFor("ClientPrompt")).thenReturn(snapshot);

        var response = this.controller.backPressureFor("ClientPrompt");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(snapshot);
    }

    private static BackPressure snapshot() {
        var ingress = new Ingress();
        ingress.setQueuedRecords(2L);
        ingress.setQueueRatePerMinute(1f);
        var egress = new Egress();
        egress.setMaxOpenMessages(1);
        egress.setCurrentOpenMessages(0);
        var backPressure = new BackPressure();
        backPressure.setIngress(ingress);
        backPressure.setEgress(egress);
        return backPressure;
    }
}
