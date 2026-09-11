package org.vader.core.server.service.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vader.common.model.vader.entity.ClientPromptOutboxMessageEntity;
import org.vader.common.model.vader.entity.OutboxMessageStatus;
import org.vader.core.server.repository.OutboxMessageRepository;

class QueueMessageProcessorTest {

    private final QueueMessageProcessor processor = new QueueMessageProcessor();

    @SuppressWarnings("unchecked")
    private final OutboxMessageRepository<ClientPromptOutboxMessageEntity> repository =
        mock(OutboxMessageRepository.class);

    private ClientPromptOutboxMessageEntity message;

    @BeforeEach
    void setUp() {
        this.message = new ClientPromptOutboxMessageEntity();
        when(this.repository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(this.repository.findById(this.message.getId()))
            .thenReturn(Optional.of(this.message));
    }

    @Test
    void claim_flipsPendingToClaimedAndStampsTheAttempt() {
        var claimed = this.processor.claim(this.repository, () -> Optional.of(this.message));

        assertThat(claimed).containsSame(this.message);
        assertThat(this.message.getStatus()).isEqualTo(OutboxMessageStatus.CLAIMED);
        assertThat(this.message.getClaimedAt()).isNotNull();
        assertThat(this.message.getAttempts()).isEqualTo(1);
        verify(this.repository).save(this.message);
    }

    @Test
    void claim_whenNothingPending_returnsEmptyAndSavesNothing() {
        var claimed = this.processor.claim(this.repository, Optional::empty);

        assertThat(claimed).isEmpty();
        verify(this.repository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void markProcessed_setsProcessedStatusAndTimestamp() {
        this.processor.markProcessed(this.repository, this.message.getId());

        assertThat(this.message.getStatus()).isEqualTo(OutboxMessageStatus.PROCESSED);
        assertThat(this.message.getProcessedAt()).isNotNull();
        verify(this.repository).save(this.message);
    }

    @Test
    void markFailed_setsFailedStatusAndReason() {
        this.processor.markFailed(this.repository, this.message.getId(), "boom");

        assertThat(this.message.getStatus()).isEqualTo(OutboxMessageStatus.FAILED);
        assertThat(this.message.getFailureReason()).isEqualTo("boom");
        verify(this.repository).save(this.message);
    }
}
