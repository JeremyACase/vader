package org.vader.core.server.service.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.vader.common.model.vader.entity.LlmRequestKind;
import org.vader.common.model.vader.entity.LlmRequestOutboxMessageEntity;
import org.vader.common.model.vader.entity.OutboxMessageStatus;
import org.vader.common.model.vader.entity.TaskAttemptStatus;
import org.vader.core.server.models.ConversationMessage;
import org.vader.core.server.models.ConversationRole;
import org.vader.core.server.models.EvaluationRequest;
import org.vader.core.server.models.InferenceTurn;
import org.vader.core.server.models.OutboxMessageEnqueuedEvent;
import org.vader.core.server.models.ReattemptDecisionRequest;
import org.vader.core.server.repository.LlmRequestOutboxMessageRepository;

class LlmRequestQueueTest {

    private LlmRequestOutboxMessageRepository messageRepository;
    private ApplicationEventPublisher eventPublisher;
    private LlmRequestQueue queue;

    @BeforeEach
    void setUp() {
        this.messageRepository = mock(LlmRequestOutboxMessageRepository.class);
        this.eventPublisher = mock(ApplicationEventPublisher.class);
        var transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));

        this.queue = new LlmRequestQueue();
        ReflectionTestUtils.setField(this.queue, "messageRepository", this.messageRepository);
        ReflectionTestUtils.setField(this.queue, "eventPublisher", this.eventPublisher);
        ReflectionTestUtils.setField(this.queue, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(this.queue, "transactionManager", transactionManager);
        ReflectionTestUtils.setField(this.queue, "resultPollIntervalMs", 5L);
        ReflectionTestUtils.setField(this.queue, "stallTimeoutSeconds", 30L);
        ReflectionTestUtils.setField(this.queue, "maxWaitSeconds", 60L);
        ReflectionTestUtils.invokeMethod(this.queue, "init");

        when(this.messageRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(this.messageRepository.findMostRecentClaimedAt())
            .thenReturn(Optional.of(java.time.OffsetDateTime.now()));
    }

    private static List<ConversationMessage> userTurn(final String text) {
        return List.of(new ConversationMessage(ConversationRole.USER, text, null, null, null));
    }

    @Test
    void submitInferenceTurn_enqueuesAndPublishesBeforeAwaitingTheResult() {
        var turn = new InferenceTurn("hello", List.of(), 5L);
        when(this.messageRepository.findById(any())).thenAnswer(invocation -> {
            var message = new LlmRequestOutboxMessageEntity();
            message.setStatus(OutboxMessageStatus.PROCESSED);
            message.setResponseJson("{\"content\":\"hello\",\"toolCalls\":[],\"tokensSpent\":5}");
            return Optional.of(message);
        });

        var result = this.queue.submitInferenceTurn(userTurn("hi"));

        assertThat(result).isEqualTo(turn);
        var messageCaptor = ArgumentCaptor.forClass(LlmRequestOutboxMessageEntity.class);
        verify(this.messageRepository).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getKind()).isEqualTo(LlmRequestKind.INFERENCE_TURN);
        assertThat(messageCaptor.getValue().getStatus()).isEqualTo(OutboxMessageStatus.PENDING);
        assertThat(messageCaptor.getValue().getRequestJson()).contains("hi");
        verify(this.eventPublisher)
            .publishEvent(new OutboxMessageEnqueuedEvent("LlmRequest"));
    }

    @Test
    void submitEvaluation_enqueuesAndPublishesBeforeAwaitingTheResult() {
        when(this.messageRepository.findById(any())).thenAnswer(invocation -> {
            var message = new LlmRequestOutboxMessageEntity();
            message.setStatus(OutboxMessageStatus.PROCESSED);
            message.setResponseJson(
                "{\"verdict\":{\"passed\":true,\"reasoning\":\"looks right\"},"
                    + "\"unreachableReason\":null}");
            return Optional.of(message);
        });
        var request = new EvaluationRequest(
            "title", "description", TaskAttemptStatus.SUCCEEDED, "result", null, List.of());

        var outcome = this.queue.submitEvaluation(request);

        assertThat(outcome.isUnreachable()).isFalse();
        assertThat(outcome.verdict().passed()).isTrue();
        var messageCaptor = ArgumentCaptor.forClass(LlmRequestOutboxMessageEntity.class);
        verify(this.messageRepository).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getKind()).isEqualTo(LlmRequestKind.EVALUATION);
        verify(this.eventPublisher)
            .publishEvent(new OutboxMessageEnqueuedEvent("LlmRequest"));
    }

    @Test
    void submitReattemptDecision_enqueuesAndPublishesBeforeAwaitingTheResult() {
        when(this.messageRepository.findById(any())).thenAnswer(invocation -> {
            var message = new LlmRequestOutboxMessageEntity();
            message.setStatus(OutboxMessageStatus.PROCESSED);
            message.setResponseJson(
                "{\"decision\":{\"shouldReattempt\":true,\"reasoning\":\"worth trying\"},"
                    + "\"unreachableReason\":null}");
            return Optional.of(message);
        });
        var request = new ReattemptDecisionRequest(
            "title", "description", 1, 3, "it broke", List.of());

        var outcome = this.queue.submitReattemptDecision(request);

        assertThat(outcome.isUnreachable()).isFalse();
        assertThat(outcome.decision().shouldReattempt()).isTrue();
        var messageCaptor = ArgumentCaptor.forClass(LlmRequestOutboxMessageEntity.class);
        verify(this.messageRepository).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getKind())
            .isEqualTo(LlmRequestKind.REATTEMPT_DECISION);
    }

    @Test
    void submitInferenceTurn_whenTheMessageSettlesFailed_throwsWithTheFailureReason() {
        when(this.messageRepository.findById(any())).thenAnswer(invocation -> {
            var message = new LlmRequestOutboxMessageEntity();
            message.setStatus(OutboxMessageStatus.FAILED);
            message.setFailureReason("boom");
            return Optional.of(message);
        });

        assertThatThrownBy(() -> this.queue.submitInferenceTurn(userTurn("hi")))
            .isInstanceOf(LlmRequestQueueException.class)
            .hasMessageContaining("boom");
    }

    @Test
    void submitInferenceTurn_whenTheOverallWaitElapses_throwsTimeout() {
        ReflectionTestUtils.setField(this.queue, "maxWaitSeconds", 0L);
        when(this.messageRepository.findById(any())).thenAnswer(invocation -> {
            var message = new LlmRequestOutboxMessageEntity();
            message.setStatus(OutboxMessageStatus.PENDING);
            return Optional.of(message);
        });

        assertThatThrownBy(() -> this.queue.submitInferenceTurn(userTurn("hi")))
            .isInstanceOf(LlmRequestQueueException.class)
            .hasMessageContaining("Timed out")
            .hasMessageContaining("wait elapsed");
    }

    @Test
    void submitInferenceTurn_whenNothingIsClaimedAnywhere_throwsStallTimeout() {
        ReflectionTestUtils.setField(this.queue, "stallTimeoutSeconds", 0L);
        when(this.messageRepository.findById(any())).thenAnswer(invocation -> {
            var message = new LlmRequestOutboxMessageEntity();
            message.setStatus(OutboxMessageStatus.PENDING);
            return Optional.of(message);
        });
        when(this.messageRepository.findMostRecentClaimedAt()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> this.queue.submitInferenceTurn(userTurn("hi")))
            .isInstanceOf(LlmRequestQueueException.class)
            .hasMessageContaining("Timed out")
            .hasMessageContaining("looks stuck");
    }

    @Test
    void submitInferenceTurn_whenTheAwaitedMessageIsClaimed_ignoresStallAndKeepsWaiting() {
        // A long-running single call (maxOpenMessages == 1, so nothing else can ever be claimed
        // while it's in flight) must not be killed by the stall heuristic once it is claimed --
        // it is, by definition, actively being worked, not stuck.
        ReflectionTestUtils.setField(this.queue, "stallTimeoutSeconds", 0L);
        ReflectionTestUtils.setField(this.queue, "maxWaitSeconds", 60L);
        var pollCount = new java.util.concurrent.atomic.AtomicInteger();
        when(this.messageRepository.findById(any())).thenAnswer(invocation -> {
            var message = new LlmRequestOutboxMessageEntity();
            if (pollCount.incrementAndGet() < 3) {
                message.setStatus(OutboxMessageStatus.CLAIMED);
            } else {
                message.setStatus(OutboxMessageStatus.PROCESSED);
                message.setResponseJson(
                    "{\"content\":\"done\",\"toolCalls\":[],\"tokensSpent\":1}");
            }
            return Optional.of(message);
        });
        when(this.messageRepository.findMostRecentClaimedAt())
            .thenReturn(Optional.of(java.time.OffsetDateTime.now().minusMinutes(5)));

        var result = this.queue.submitInferenceTurn(userTurn("hi"));

        assertThat(result.content()).isEqualTo("done");
        assertThat(pollCount.get()).isEqualTo(3);
    }

    @Test
    void submitInferenceTurn_whenClaimedMessageOutlastsTheMaxWait_throwsTheGenericTimeout() {
        ReflectionTestUtils.setField(this.queue, "stallTimeoutSeconds", 0L);
        ReflectionTestUtils.setField(this.queue, "maxWaitSeconds", 0L);
        when(this.messageRepository.findById(any())).thenAnswer(invocation -> {
            var message = new LlmRequestOutboxMessageEntity();
            message.setStatus(OutboxMessageStatus.CLAIMED);
            return Optional.of(message);
        });
        when(this.messageRepository.findMostRecentClaimedAt())
            .thenReturn(Optional.of(java.time.OffsetDateTime.now().minusMinutes(5)));

        assertThatThrownBy(() -> this.queue.submitInferenceTurn(userTurn("hi")))
            .isInstanceOf(LlmRequestQueueException.class)
            .hasMessageContaining("Timed out")
            .hasMessageContaining("wait elapsed")
            .hasMessageNotContaining("looks stuck");
    }

    @Test
    void submitInferenceTurn_whenTheQueueIsDeepButStillClaimingRecently_keepsWaiting() {
        // A queue that keeps making progress (a fresh claim observed on every poll) must not be
        // treated as stalled just because it hasn't finished this specific request yet.
        ReflectionTestUtils.setField(this.queue, "stallTimeoutSeconds", 30L);
        ReflectionTestUtils.setField(this.queue, "maxWaitSeconds", 60L);
        var pollCount = new java.util.concurrent.atomic.AtomicInteger();
        when(this.messageRepository.findById(any())).thenAnswer(invocation -> {
            var message = new LlmRequestOutboxMessageEntity();
            if (pollCount.incrementAndGet() < 3) {
                message.setStatus(OutboxMessageStatus.PENDING);
            } else {
                message.setStatus(OutboxMessageStatus.PROCESSED);
                message.setResponseJson(
                    "{\"content\":\"done\",\"toolCalls\":[],\"tokensSpent\":1}");
            }
            return Optional.of(message);
        });
        when(this.messageRepository.findMostRecentClaimedAt())
            .thenAnswer(invocation -> Optional.of(java.time.OffsetDateTime.now()));

        var result = this.queue.submitInferenceTurn(userTurn("hi"));

        assertThat(result.content()).isEqualTo("done");
        assertThat(pollCount.get()).isEqualTo(3);
    }
}
