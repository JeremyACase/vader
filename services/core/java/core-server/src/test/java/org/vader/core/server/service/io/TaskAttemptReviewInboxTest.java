package org.vader.core.server.service.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.model.vader.entity.OutboxMessageStatus;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskAttemptReviewOutboxMessageEntity;
import org.vader.core.server.exceptions.OrchestratorUnavailableException;
import org.vader.core.server.repository.TaskAttemptReviewOutboxMessageRepository;
import org.vader.core.server.service.agent.TaskAttemptReviewRetryService;
import org.vader.core.server.service.agent.TaskAttemptReviewService;

class TaskAttemptReviewInboxTest {

    private TaskAttemptReviewOutboxMessageRepository messageRepository;
    private QueueMessageProcessor processor;
    private TaskAttemptReviewService reviewService;
    private TaskAttemptReviewRetryService retryService;
    private TaskAttemptReviewInbox inbox;

    @BeforeEach
    void setUp() {
        this.messageRepository = mock(TaskAttemptReviewOutboxMessageRepository.class);
        this.processor = mock(QueueMessageProcessor.class);
        this.reviewService = mock(TaskAttemptReviewService.class);
        this.retryService = mock(TaskAttemptReviewRetryService.class);
        this.inbox = new TaskAttemptReviewInbox();
        ReflectionTestUtils.setField(this.inbox, "messageRepository", this.messageRepository);
        ReflectionTestUtils.setField(this.inbox, "processor", this.processor);
        ReflectionTestUtils.setField(this.inbox, "reviewService", this.reviewService);
        ReflectionTestUtils.setField(this.inbox, "retryService", this.retryService);
        ReflectionTestUtils.setField(this.inbox, "maxConcurrency", 1);
    }

    private static TaskAttemptReviewOutboxMessageEntity reviewMessage() {
        var attempt = new TaskAttemptEntity();
        attempt.setId(UUID.randomUUID().toString());
        var message = new TaskAttemptReviewOutboxMessageEntity();
        message.setId(UUID.randomUUID().toString());
        message.setTaskAttempt(attempt);
        return message;
    }

    /** Has the processor claim whatever the inbox's own finder supplies, exactly once. */
    @SuppressWarnings("unchecked")
    private void claimViaTheInboxesFinderOnce() {
        when(this.processor.claim(eq(this.messageRepository), any(Supplier.class)))
            .thenAnswer(call -> ((Supplier<Optional<?>>) call.getArgument(1)).get())
            .thenReturn(Optional.empty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void pop_claimsOnlyReviewThatIsDue() {
        var due = reviewMessage();
        when(this.messageRepository.findDue(eq(OutboxMessageStatus.PENDING), any(), any()))
            .thenReturn(List.of(due));
        when(this.processor.claim(eq(this.messageRepository), any(Supplier.class)))
            .thenAnswer(call -> ((Supplier<Optional<?>>) call.getArgument(1)).get());

        var claimed = this.inbox.pop();

        assertThat(claimed).contains(due);
        verify(this.messageRepository, never())
            .findFirstByStatusOrderByCreatedAtAsc(any());
    }

    @Test
    void drain_whenTheLlmIsUnavailable_defersTheReviewInsteadOfFailingIt() {
        var message = reviewMessage();
        when(this.messageRepository.findDue(any(), any(), any())).thenReturn(List.of(message));
        claimViaTheInboxesFinderOnce();
        doThrow(new OrchestratorUnavailableException(
                "Could not reach the local LLM to evaluate this attempt.",
                new IllegalStateException("connection refused")))
            .when(this.reviewService).review(message.getTaskAttempt().getId());

        this.inbox.drain();

        var reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(this.retryService).deferForLlmOutage(eq(message.getId()), reasonCaptor.capture());
        assertThat(reasonCaptor.getValue()).contains("Could not reach the local LLM");
        verify(this.processor, never()).markFailed(any(), anyString(), anyString());
        verify(this.processor, never()).markProcessed(any(), anyString());
    }

    @Test
    void drain_whenReviewFailsForAnyOtherReason_stillMarksItFailed() {
        var message = reviewMessage();
        when(this.messageRepository.findDue(any(), any(), any())).thenReturn(List.of(message));
        claimViaTheInboxesFinderOnce();
        doThrow(new IllegalStateException("bug"))
            .when(this.reviewService).review(message.getTaskAttempt().getId());

        this.inbox.drain();

        verify(this.processor).markFailed(
            eq(this.messageRepository), eq(message.getId()), anyString());
        verify(this.retryService, never()).deferForLlmOutage(anyString(), anyString());
    }
}
