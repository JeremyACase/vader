package org.vader.core.server.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.instancio.Instancio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.model.vader.dto.ClientPromptOutboxMessage;
import org.vader.common.model.vader.entity.OutboxMessageStatus;
import org.vader.core.server.messaging.model.QueueMessagePage;
import org.vader.core.server.messaging.model.QueueSummary;

class QueueInspectionControllerTest {

    private QueueInspectionRegistry registry;
    private AbstractQueueInspectionAdapter<?, ?> adapter;
    private QueueInspectionController controller;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        this.registry = mock(QueueInspectionRegistry.class);
        this.adapter = mock(AbstractQueueInspectionAdapter.class);
        this.controller = new QueueInspectionController();
        ReflectionTestUtils.setField(this.controller, "registry", this.registry);
    }

    @Test
    void queues_summarizesEveryRegisteredQueue() {
        var summary = new QueueSummary("ClientPrompt", 1, Map.of());
        when(this.registry.all()).thenReturn(List.of(this.adapter));
        when(this.adapter.summary()).thenReturn(summary);

        assertThat(this.controller.queues()).containsExactly(summary);
    }

    @Test
    void messages_parsesTheStatusFilter() {
        var page = new QueueMessagePage(List.of(), 0, 10, 0, 0);
        doReturnAdapter("ClientPrompt");
        when(this.adapter.messages(OutboxMessageStatus.FAILED, 0, 10)).thenReturn(page);

        assertThat(this.controller.messages("ClientPrompt", 0, 10, "FAILED")).isSameAs(page);
    }

    @Test
    void messages_withoutStatus_passesNoFilter() {
        doReturnAdapter("ClientPrompt");

        this.controller.messages("ClientPrompt", 2, 25, null);

        verify(this.adapter).messages(null, 2, 25);
    }

    @Test
    void messages_withUnknownStatus_isRejected() {
        doReturnAdapter("ClientPrompt");

        assertThatThrownBy(() -> this.controller.messages("ClientPrompt", 0, 10, "BOGUS"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void message_whenPresent_returnsIt() {
        var message = Instancio.create(ClientPromptOutboxMessage.class);
        doReturnAdapter("ClientPrompt");
        when(((AbstractQueueInspectionAdapter) this.adapter).message(message.getId()))
            .thenReturn(Optional.of(message));

        var response = this.controller.message("ClientPrompt", message.getId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(message);
    }

    @Test
    void message_whenAbsent_isNoContent() {
        doReturnAdapter("ClientPrompt");
        when(this.adapter.message("missing")).thenReturn(Optional.empty());

        var response = this.controller.message("ClientPrompt", "missing");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void doReturnAdapter(final String name) {
        when(this.registry.require(name)).thenReturn((AbstractQueueInspectionAdapter) this.adapter);
    }
}
