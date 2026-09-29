package org.vader.core.server.llm;

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
import org.vader.core.server.messaging.OutboxMessageEnqueuedEvent;
import org.vader.core.server.review.EvaluationLlmExecutor;
import org.vader.core.server.review.model.EvaluationRequest;
import org.vader.core.server.taskagent.InferenceTurnLlmExecutor;
import org.vader.core.server.taskagent.model.ConversationMessage;
import org.vader.core.server.taskagent.model.ConversationRole;
import org.vader.core.server.taskagent.model.InferenceTurn;
import org.vader.core.server.workflow.WorkflowSynthesisLlmExecutor;
import org.vader.core.server.workflow.model.WorkflowSynthesisRequest;

class LlmRequestQueueTest {

    private static final EvaluationRequest EVALUATION_REQUEST = new EvaluationRequest(
        "title", "description", TaskAttemptStatus.SUCCEEDED, "result", null, List.of(), null);

    private LlmRequestOutboxMessageRepository messageRepository;
    private ApplicationEventPublisher eventPublisher;
    private LlmRequestQueue queue;

    @BeforeEach
    void setUp() {
        this.messageRepository = mock(LlmRequestOutboxMessageRepository.class);
        this.eventPublisher = mock(ApplicationEventPublisher.class);
        var transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));

        var inferenceTurnExecutor = mock(InferenceTurnLlmExecutor.class);
        when(inferenceTurnExecutor.kind()).thenReturn(LlmRequestKind.INFERENCE_TURN);
        var evaluationExecutor = mock(EvaluationLlmExecutor.class);
        when(evaluationExecutor.kind()).thenReturn(LlmRequestKind.EVALUATION);
        var executorRegistry = mock(LlmExecutorRegistry.class);
        when(executorRegistry.forType(InferenceTurnLlmExecutor.class))
            .thenReturn(inferenceTurnExecutor);
        when(executorRegistry.forType(EvaluationLlmExecutor.class)).thenReturn(evaluationExecutor);

        this.queue = new LlmRequestQueue();
        ReflectionTestUtils.setField(this.queue, "messageRepository", this.messageRepository);
        ReflectionTestUtils.setField(this.queue, "eventPublisher", this.eventPublisher);
        ReflectionTestUtils.setField(this.queue, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(this.queue, "transactionManager", transactionManager);
        ReflectionTestUtils.setField(this.queue, "executorRegistry", executorRegistry);
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

    private void respondWith(final String responseJson) {
        when(this.messageRepository.findById(any())).thenAnswer(invocation -> {
            var message = new LlmRequestOutboxMessageEntity();
            message.setStatus(OutboxMessageStatus.PROCESSED);
            message.setResponseJson(responseJson);
            return Optional.of(message);
        });
    }

    @Test
    void submit_enqueuesUnderTheExecutorsKindAndPublishesBeforeAwaitingTheResult() {
        this.respondWith(
            "{\"value\":{\"content\":\"hello\",\"toolCalls\":[],\"tokensSpent\":5,"
                + "\"finishReason\":\"stop\"},\"unreachableReason\":null}");

        var result = this.queue.submit(InferenceTurnLlmExecutor.class, userTurn("hi"));

        assertThat(result).isEqualTo(new InferenceTurn("hello", List.of(), 5L, "stop"));
        var messageCaptor = ArgumentCaptor.forClass(LlmRequestOutboxMessageEntity.class);
        verify(this.messageRepository).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getKind()).isEqualTo(LlmRequestKind.INFERENCE_TURN);
        assertThat(messageCaptor.getValue().getStatus()).isEqualTo(OutboxMessageStatus.PENDING);
        assertThat(messageCaptor.getValue().getRequestJson()).contains("hi");
        verify(this.eventPublisher)
            .publishEvent(new OutboxMessageEnqueuedEvent("LlmRequest"));
    }

    @Test
    void submit_decodesTheResponseAsTheExecutorsResponseType() {
        this.respondWith(
            "{\"value\":{\"passed\":true,\"reasoning\":\"looks right\"},"
                + "\"unreachableReason\":null}");

        var verdict = this.queue.submit(EvaluationLlmExecutor.class, EVALUATION_REQUEST);

        assertThat(verdict.passed()).isTrue();
        assertThat(verdict.reasoning()).isEqualTo("looks right");
        var messageCaptor = ArgumentCaptor.forClass(LlmRequestOutboxMessageEntity.class);
        verify(this.messageRepository).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getKind()).isEqualTo(LlmRequestKind.EVALUATION);
    }

    @Test
    void submit_decodesPlainTextResponses() {
        var synthesisExecutor = mock(WorkflowSynthesisLlmExecutor.class);
        when(synthesisExecutor.kind()).thenReturn(LlmRequestKind.WORKFLOW_SYNTHESIS);
        var executorRegistry = (LlmExecutorRegistry) ReflectionTestUtils.getField(
            this.queue, "executorRegistry");
        when(executorRegistry.forType(WorkflowSynthesisLlmExecutor.class))
            .thenReturn(synthesisExecutor);
        this.respondWith("{\"value\":\"Quarterly sales by region.\",\"unreachableReason\":null}");

        var answer = this.queue.submit(
            WorkflowSynthesisLlmExecutor.class,
            new WorkflowSynthesisRequest("prompt", "objective", List.of()));

        assertThat(answer).isEqualTo("Quarterly sales by region.");
    }

    @Test
    void submit_whenTheResponseValueIsNull_returnsNull() {
        this.respondWith("{\"value\":null,\"unreachableReason\":null}");

        var verdict = this.queue.submit(EvaluationLlmExecutor.class, EVALUATION_REQUEST);

        assertThat(verdict).isNull();
    }

    @Test
    void submit_whenTheLlmWasUnreachable_throwsOrchestratorUnavailable() {
        this.respondWith("{\"value\":null,\"unreachableReason\":\"connection refused\"}");

        assertThatThrownBy(
            () -> this.queue.submit(EvaluationLlmExecutor.class, EVALUATION_REQUEST))
            .isInstanceOf(OrchestratorUnavailableException.class)
            .hasMessageContaining("local LLM")
            .hasMessageContaining("EVALUATION")
            .hasRootCauseMessage("connection refused");
    }

    @Test
    void submit_whenTheMessageSettlesFailed_throwsWithTheFailureReason() {
        when(this.messageRepository.findById(any())).thenAnswer(invocation -> {
            var message = new LlmRequestOutboxMessageEntity();
            message.setStatus(OutboxMessageStatus.FAILED);
            message.setFailureReason("boom");
            return Optional.of(message);
        });

        // Processed-and-failed is not a timeout: retrying it would likely fail the same way.
        assertThatThrownBy(() -> this.queue.submit(InferenceTurnLlmExecutor.class, userTurn("hi")))
            .isInstanceOf(LlmRequestQueueException.class)
            .isNotInstanceOf(LlmRequestTimeoutException.class)
            .hasMessageContaining("boom");
    }

    @Test
    void submit_whenTheOverallWaitElapses_throwsTimeout() {
        ReflectionTestUtils.setField(this.queue, "maxWaitSeconds", 0L);
        when(this.messageRepository.findById(any())).thenAnswer(invocation -> {
            var message = new LlmRequestOutboxMessageEntity();
            message.setStatus(OutboxMessageStatus.PENDING);
            return Optional.of(message);
        });

        assertThatThrownBy(() -> this.queue.submit(InferenceTurnLlmExecutor.class, userTurn("hi")))
            .isInstanceOf(LlmRequestTimeoutException.class)
            .hasMessageContaining("Timed out")
            .hasMessageContaining("wait elapsed");
    }

    @Test
    void submit_whenNothingIsClaimedAnywhere_throwsStallTimeout() {
        ReflectionTestUtils.setField(this.queue, "stallTimeoutSeconds", 0L);
        when(this.messageRepository.findById(any())).thenAnswer(invocation -> {
            var message = new LlmRequestOutboxMessageEntity();
            message.setStatus(OutboxMessageStatus.PENDING);
            return Optional.of(message);
        });
        when(this.messageRepository.findMostRecentClaimedAt()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> this.queue.submit(InferenceTurnLlmExecutor.class, userTurn("hi")))
            .isInstanceOf(LlmRequestTimeoutException.class)
            .hasMessageContaining("Timed out")
            .hasMessageContaining("looks stuck");
    }

    @Test
    void submit_whenTheLastClaimPredatesThisWait_givesTheFullStallWindow() {
        // The last claim was minutes ago because one long request held the single worker the
        // whole time, and this request was queued just as it finished. It must get the full
        // stall window from when it started waiting -- not be judged stalled on its first poll.
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
                    "{\"value\":{\"content\":\"done\",\"toolCalls\":[],\"tokensSpent\":1}}");
            }
            return Optional.of(message);
        });
        when(this.messageRepository.findMostRecentClaimedAt())
            .thenReturn(Optional.of(java.time.OffsetDateTime.now().minusMinutes(5)));

        var result = this.queue.submit(InferenceTurnLlmExecutor.class, userTurn("hi"));

        assertThat(result.content()).isEqualTo("done");
        assertThat(pollCount.get()).isEqualTo(3);
    }

    @Test
    void submit_whenTheAwaitedMessageIsClaimed_ignoresStallAndKeepsWaiting() {
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
                    "{\"value\":{\"content\":\"done\",\"toolCalls\":[],\"tokensSpent\":1}}");
            }
            return Optional.of(message);
        });
        when(this.messageRepository.findMostRecentClaimedAt())
            .thenReturn(Optional.of(java.time.OffsetDateTime.now().minusMinutes(5)));

        var result = this.queue.submit(InferenceTurnLlmExecutor.class, userTurn("hi"));

        assertThat(result.content()).isEqualTo("done");
        assertThat(pollCount.get()).isEqualTo(3);
    }

    @Test
    void submit_whenClaimedMessageOutlastsTheMaxWait_throwsTheGenericTimeout() {
        ReflectionTestUtils.setField(this.queue, "stallTimeoutSeconds", 0L);
        ReflectionTestUtils.setField(this.queue, "maxWaitSeconds", 0L);
        when(this.messageRepository.findById(any())).thenAnswer(invocation -> {
            var message = new LlmRequestOutboxMessageEntity();
            message.setStatus(OutboxMessageStatus.CLAIMED);
            return Optional.of(message);
        });
        when(this.messageRepository.findMostRecentClaimedAt())
            .thenReturn(Optional.of(java.time.OffsetDateTime.now().minusMinutes(5)));

        assertThatThrownBy(() -> this.queue.submit(InferenceTurnLlmExecutor.class, userTurn("hi")))
            .isInstanceOf(LlmRequestQueueException.class)
            .hasMessageContaining("Timed out")
            .hasMessageContaining("wait elapsed")
            .hasMessageNotContaining("looks stuck");
    }

    @Test
    void submit_whenTheQueueIsDeepButStillClaimingRecently_keepsWaiting() {
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
                    "{\"value\":{\"content\":\"done\",\"toolCalls\":[],\"tokensSpent\":1}}");
            }
            return Optional.of(message);
        });
        when(this.messageRepository.findMostRecentClaimedAt())
            .thenAnswer(invocation -> Optional.of(java.time.OffsetDateTime.now()));

        var result = this.queue.submit(InferenceTurnLlmExecutor.class, userTurn("hi"));

        assertThat(result.content()).isEqualTo("done");
        assertThat(pollCount.get()).isEqualTo(3);
    }
}
