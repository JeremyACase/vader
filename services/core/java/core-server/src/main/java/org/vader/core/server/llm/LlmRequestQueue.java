package org.vader.core.server.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.vader.common.model.vader.entity.LlmRequestKind;
import org.vader.common.model.vader.entity.LlmRequestOutboxMessageEntity;
import org.vader.common.model.vader.entity.OutboxMessageStatus;
import org.vader.core.server.llm.interfaces.InterfaceLlmExecutor;
import org.vader.core.server.messaging.OutboxMessageEnqueuedEvent;

/**
 * Front door for every LLM call: enqueues a durable {@code LlmRequest} message and blocks until
 * whichever replica's {@link LlmRequestInbox} processes it has written a response back.
 *
 * <p>The one-request-in-flight limit lives in the database, so it holds across any number of
 * replicas.</p>
 *
 * <p>The enqueue and every poll run in their own
 * {@link TransactionDefinition#PROPAGATION_REQUIRES_NEW} transaction, via a
 * {@link TransactionTemplate} because {@code @Transactional} is bypassed on self-calls. The
 * enqueue must commit before any inbox can claim it, each poll must read fresh rather than from
 * the session cache, and the wait must not hold the caller's transaction and connection open.</p>
 *
 * <p>Patience is stall-based, so a deep-but-healthy queue never fails a caller: a pending request
 * gives up only when nothing on the queue, on any replica, has been claimed for
 * {@code stallTimeoutSeconds}. A claimed request is exempt from that check -- with a single
 * slot, its own claim is the latest there can be -- and is bounded only by
 * {@code maxWaitSeconds}.</p>
 */
@Service
public class LlmRequestQueue {

    private static final String QUEUED_MODEL_TYPE = "LlmRequest";

    @Autowired
    private LlmRequestOutboxMessageRepository messageRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private LlmExecutorRegistry executorRegistry;

    @Value("${vader.llm.request-queue.result-poll-interval-ms:100}")
    private long resultPollIntervalMs;

    @Value("${vader.llm.request-queue.stall-timeout-seconds:60}")
    private long stallTimeoutSeconds;

    @Value("${vader.llm.request-queue.max-wait-seconds:1800}")
    private long maxWaitSeconds;

    private TransactionTemplate requiresNewTransaction;

    @PostConstruct
    void init() {
        this.requiresNewTransaction = new TransactionTemplate(this.transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(
            TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Submits one call for the given executor to perform, and blocks until it completes.
     *
     * @param executorType which executor performs the call
     * @param request the call's request
     * @param <Q> the request type
     * @param <R> the response type
     * @param <E> the executor type
     * @return the executor's response
     * @throws OrchestratorUnavailableException if the LLM could not be reached
     * @throws LlmRequestTimeoutException if the queue gave up waiting
     * @throws LlmRequestQueueException if the call failed for any other reason
     */
    public <Q, R, E extends InterfaceLlmExecutor<Q, R>> R submit(
            final Class<E> executorType, final Q request) {
        var executor = this.executorRegistry.forType(executorType);
        var messageId = this.enqueue(executor.kind(), this.toJson(request));
        var envelope = this.fromJson(this.awaitResult(messageId), LlmResponseEnvelope.class);
        return this.responseOf(executor, envelope);
    }

    private <R> R responseOf(
            final InterfaceLlmExecutor<?, R> executor, final LlmResponseEnvelope envelope) {
        if (envelope.isUnreachable()) {
            throw new OrchestratorUnavailableException(
                "Could not reach the local LLM for its " + executor.kind() + " call.",
                new IllegalStateException(envelope.unreachableReason()));
        }
        return this.objectMapper.convertValue(
            envelope.value(),
            this.objectMapper.constructType(LlmExecutorRegistry.responseTypeOf(executor)));
    }

    private String enqueue(final LlmRequestKind kind, final String requestJson) {
        return this.requiresNewTransaction.execute(status -> {
            var message = new LlmRequestOutboxMessageEntity();
            message.setKind(kind);
            message.setRequestJson(requestJson);
            message.setStatus(OutboxMessageStatus.PENDING);
            this.messageRepository.save(message);
            this.eventPublisher.publishEvent(new OutboxMessageEnqueuedEvent(QUEUED_MODEL_TYPE));
            return message.getId();
        });
    }

    private String awaitResult(final String messageId) {
        var waitStarted = Instant.now();
        var overallDeadline = waitStarted.plusSeconds(this.maxWaitSeconds);
        var message = this.fetchFresh(messageId);
        while (isStillOpen(message)
                && this.stillHasPatience(waitStarted, overallDeadline, message)) {
            sleep(this.resultPollIntervalMs);
            message = this.fetchFresh(messageId);
        }
        return this.resultOf(messageId, message, waitStarted);
    }

    private boolean stillHasPatience(
            final Instant waitStarted, final Instant overallDeadline,
            final LlmRequestOutboxMessageEntity message) {
        var now = Instant.now();
        return now.isBefore(overallDeadline)
            && (isClaimed(message) || !this.isStalled(waitStarted, now));
    }

    /**
     * Whether nothing has been claimed for {@code stallTimeoutSeconds}, counting from the later of
     * the last claim and the start of this wait: a claim older than the wait says nothing about
     * whether this request will be picked up. Only meaningful while the request is pending.
     */
    private boolean isStalled(final Instant waitStarted, final Instant now) {
        var lastActivity = this.fetchLastClaimedAt()
            .filter(lastClaimedAt -> lastClaimedAt.isAfter(waitStarted))
            .orElse(waitStarted);
        return now.isAfter(lastActivity.plusSeconds(this.stallTimeoutSeconds));
    }

    private static boolean isClaimed(final LlmRequestOutboxMessageEntity message) {
        return message.getStatus() == OutboxMessageStatus.CLAIMED;
    }

    private Optional<Instant> fetchLastClaimedAt() {
        return this.requiresNewTransaction.execute(status ->
            this.messageRepository.findMostRecentClaimedAt().map(offsetDateTime ->
                offsetDateTime.toInstant()));
    }

    private String resultOf(
            final String messageId, final LlmRequestOutboxMessageEntity message,
            final Instant waitStarted) {
        String result;
        if (isStillOpen(message)) {
            throw new LlmRequestTimeoutException(
                "Timed out waiting for LLM request " + messageId + " to be processed: "
                    + this.timeoutReasonFor(message, waitStarted));
        } else if (message.getStatus() == OutboxMessageStatus.FAILED) {
            throw new LlmRequestQueueException(
                "LLM request " + messageId + " failed: " + message.getFailureReason());
        } else {
            result = message.getResponseJson();
        }
        return result;
    }

    private String timeoutReasonFor(
            final LlmRequestOutboxMessageEntity message, final Instant waitStarted) {
        var isStalledPending = !isClaimed(message) && this.isStalled(waitStarted, Instant.now());
        return isStalledPending
            ? "no request on this queue has been claimed for " + this.stallTimeoutSeconds
                + "s -- the local LLM backend looks stuck"
            : "the overall " + this.maxWaitSeconds + "s wait elapsed";
    }

    private static boolean isStillOpen(final LlmRequestOutboxMessageEntity message) {
        return message.getStatus() == OutboxMessageStatus.PENDING
            || message.getStatus() == OutboxMessageStatus.CLAIMED;
    }

    private LlmRequestOutboxMessageEntity fetchFresh(final String messageId) {
        return this.requiresNewTransaction.execute(status ->
            this.messageRepository.findById(messageId).orElseThrow(() ->
                new LlmRequestQueueException("LLM request " + messageId + " vanished")));
    }

    private static void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmRequestQueueException("Interrupted while awaiting an LLM response", e);
        }
    }

    private String toJson(final Object value) {
        try {
            return this.objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new LlmRequestQueueException("Could not serialize LLM request", e);
        }
    }

    private <T> T fromJson(final String json, final Class<T> type) {
        try {
            return this.objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new LlmRequestQueueException("Could not deserialize LLM response", e);
        }
    }
}
