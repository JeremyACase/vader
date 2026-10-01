package org.vader.core.server.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class QueueInspectionRegistryTest {

    private AbstractQueueInspectionAdapter<?, ?> clientPrompts;
    private AbstractQueueInspectionAdapter<?, ?> llmRequests;
    private QueueInspectionRegistry registry;

    @BeforeEach
    void setUp() {
        this.clientPrompts = adapterNamed("ClientPrompt");
        this.llmRequests = adapterNamed("LlmRequest");
        this.registry = new QueueInspectionRegistry();
        ReflectionTestUtils.setField(
            this.registry, "adapters", List.of(this.clientPrompts, this.llmRequests));
        this.registry.index();
    }

    @Test
    void all_returnsEveryAdapterInRegistrationOrder() {
        assertThat(this.registry.all()).containsExactly(this.clientPrompts, this.llmRequests);
    }

    @Test
    void require_resolvesByQueueName() {
        assertThat(this.registry.require("LlmRequest")).isSameAs(this.llmRequests);
    }

    @Test
    void require_withUnknownName_isRejected() {
        assertThatThrownBy(() -> this.registry.require("Bogus"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Bogus");
    }

    private static AbstractQueueInspectionAdapter<?, ?> adapterNamed(final String name) {
        var adapter = mock(AbstractQueueInspectionAdapter.class);
        when(adapter.queueName()).thenReturn(name);
        return adapter;
    }
}
