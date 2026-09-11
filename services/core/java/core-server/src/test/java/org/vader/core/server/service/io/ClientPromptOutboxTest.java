package org.vader.core.server.service.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.library.implementation.service.builder.VaderIngressResponseBuilder;
import org.vader.common.model.vader.entity.ClientPromptEntity;
import org.vader.common.model.vader.entity.ClientPromptOutboxMessageEntity;
import org.vader.common.model.vader.entity.OutboxMessageStatus;
import org.vader.core.server.repository.ClientPromptOutboxMessageRepository;

class ClientPromptOutboxTest {

    private static final String PROMPT_ID = "aaaaaaaa-1111-2222-3333-444444444444";

    private ClientPromptOutboxMessageRepository messageRepository;
    private ApplicationEventPublisher eventPublisher;
    private ClientPromptOutbox outbox;

    @BeforeEach
    void setUp() {
        this.messageRepository = mock(ClientPromptOutboxMessageRepository.class);
        this.eventPublisher = mock(ApplicationEventPublisher.class);
        this.outbox = new ClientPromptOutbox();
        ReflectionTestUtils.setField(this.outbox, "messageRepository", this.messageRepository);
        ReflectionTestUtils.setField(
            this.outbox, "ingressResponseBuilder", new VaderIngressResponseBuilder());
        ReflectionTestUtils.setField(this.outbox, "eventPublisher", this.eventPublisher);
        when(this.messageRepository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    private static ClientPromptEntity prompt() {
        var prompt = new ClientPromptEntity();
        prompt.setId(PROMPT_ID);
        prompt.setText("decompose this");
        return prompt;
    }

    @Test
    void enqueue_persistsPendingMessageWrappingThePrompt() {
        var prompt = prompt();

        this.outbox.enqueue(prompt);

        var captor = ArgumentCaptor.forClass(ClientPromptOutboxMessageEntity.class);
        verify(this.messageRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OutboxMessageStatus.PENDING);
        assertThat(captor.getValue().getClientPrompt()).isSameAs(prompt);
    }

    @Test
    void enqueue_announcesTheEnqueueForTheClientPromptModelType() {
        this.outbox.enqueue(prompt());

        verify(this.eventPublisher)
            .publishEvent(new OutboxMessageEnqueuedEvent("ClientPrompt"));
    }

    @Test
    void enqueue_returnsReceiptIdentifyingThePrompt() {
        var receipt = this.outbox.enqueue(prompt());

        assertThat(receipt.getId()).isEqualTo(PROMPT_ID);
        assertThat(receipt.getPayloadModelType()).isEqualTo("ClientPrompt");
    }
}
