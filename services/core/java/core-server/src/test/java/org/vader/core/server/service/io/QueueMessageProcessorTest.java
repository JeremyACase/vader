package org.vader.core.server.service.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    void claim_winningTheConditionalUpdate_returnsTheFreshlyReReadMessage() {
        when(this.repository.claimIfStillPending(
            eq(this.message.getId()), any(), eq(OutboxMessageStatus.PENDING),
            eq(OutboxMessageStatus.CLAIMED)))
            .thenReturn(1);

        var claimed = this.processor.claim(this.repository, () -> Optional.of(this.message));

        assertThat(claimed).containsSame(this.message);
        verify(this.repository).claimIfStillPending(
            eq(this.message.getId()), any(), eq(OutboxMessageStatus.PENDING),
            eq(OutboxMessageStatus.CLAIMED));
    }

    @Test
    void claim_whenAnotherReplicaWinsTheRace_triesTheNextCandidateTheFinderReports() {
        var lostRace = new ClientPromptOutboxMessageEntity();
        var wonRace = new ClientPromptOutboxMessageEntity();
        when(this.repository.claimIfStillPending(
            eq(lostRace.getId()), any(), any(), any())).thenReturn(0);
        when(this.repository.claimIfStillPending(
            eq(wonRace.getId()), any(), any(), any())).thenReturn(1);
        when(this.repository.findById(wonRace.getId())).thenReturn(Optional.of(wonRace));
        when(this.repository.findFirstByStatusOrderByCreatedAtAsc(OutboxMessageStatus.PENDING))
            .thenReturn(Optional.of(lostRace), Optional.of(wonRace));

        var claimed = this.processor.claim(this.repository,
            () -> this.repository.findFirstByStatusOrderByCreatedAtAsc(
                OutboxMessageStatus.PENDING));

        assertThat(claimed).containsSame(wonRace);
    }

    @Test
    void claim_whenNothingPending_returnsEmptyAndNeverAttemptsAnUpdate() {
        var claimed = this.processor.claim(this.repository, Optional::empty);

        assertThat(claimed).isEmpty();
        verify(this.repository, never()).claimIfStillPending(any(), any(), any(), any());
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
