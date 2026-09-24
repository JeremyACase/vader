package org.vader.core.server.service.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.List;
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
import org.vader.core.server.models.ConversationMessage;
import org.vader.core.server.models.DecompositionRequest;
import org.vader.core.server.models.EvaluationRequest;
import org.vader.core.server.models.InferenceTurn;
import org.vader.core.server.models.OutboxMessageEnqueuedEvent;
import org.vader.core.server.models.ReattemptDecisionRequest;
import org.vader.core.server.models.TaskPlanRefinementRequest;
import org.vader.core.server.repository.LlmRequestOutboxMessageRepository;

/**
 * Front door for every outbound local-LLM call: enqueues a durable {@code LlmRequest} message and
 * blocks the calling thread until whichever replica's {@link LlmRequestInbox} claims and
 * processes it -- possibly this replica, possibly another -- has written a response back.
 *
 * <p>This replaces an earlier in-process, single-JVM queue: the actual serialization (only one
 * request in flight at a time) now lives entirely in the database, via the same
 * claim/process/mark lifecycle every other inbox/outbox queue in this codebase uses, gated to
 * {@code maxOpenMessages() == 1} system-wide. That makes it correct across any number of
 * {@code core-server} replicas -- an in-JVM queue could only ever serialize the requests one
 * replica happened to receive.</p>
 *
 * <p>The enqueue write and every poll read each run in their own, brand-new transaction
 * ({@link TransactionDefinition#PROPAGATION_REQUIRES_NEW} via an explicit {@link
 * TransactionTemplate}, not a {@code @Transactional} annotation -- the annotation only takes
 * effect through the Spring AOP proxy, which a method calling another method on {@code this}
 * never goes through). Three things depend on that: the enqueue must commit immediately so some
 * inbox (in this replica or another) can actually see and claim it; each poll must issue a fresh
 * read rather than reuse a cached, stale copy of the entity from an earlier read in the same
 * Hibernate session; and the wait must not pin the caller's own ambient transaction (e.g.
 * {@code TaskAgentService.recordInferenceTurn}'s) open, and its DB connection with it, for
 * however long the LLM takes to answer.</p>
 *
 * <p>The wait itself is stall-based, not a fixed per-request clock: a deep-but-healthy queue must
 * not fail a caller purely for having waited its turn behind others. Patience is judged by
 * {@link LlmRequestOutboxMessageRepository#findMostRecentClaimedAt} -- the most recent claim
 * <em>anywhere</em> on this queue, across every replica, mirroring the same stall-detection
 * philosophy the Rust harness already applies to its own turn loop
 * ({@code StallDetector}, catching a harness repeating the same tool call), just applied here to
 * "is the one global worker slot still doing anything." Only when nothing has been claimed for
 * {@code stallTimeoutSeconds} does this give up -- that is the actual signal the LLM backend is
 * stuck, not merely busy. {@code maxWaitSeconds} remains as an absolute backstop so a queue that
 * somehow never stops making slow progress cannot block a caller forever.</p>
 *
 * <p>Crucially, that stall check only ever applies while the awaited message is still
 * {@code PENDING}. {@link LlmRequestInbox#maxOpenMessages()} is hardcoded to {@code 1}, so once
 * a message is claimed it is -- by definition -- the single thing "the most recent claim" could
 * possibly refer to; its own claim timestamp stops moving the instant it starts being processed,
 * not because the backend went quiet but because there is nothing else left to claim. Applying
 * the stall heuristic past that point would fail any single call that legitimately runs longer
 * than {@code stallTimeoutSeconds} (a cold local model loading into memory on CPU easily can),
 * misreporting a slow-but-working backend as "unreachable." Once claimed, a request is trusted
 * to actually be in flight and is bounded only by the generous {@code maxWaitSeconds} backstop.</p>
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
     * Submits one harness turn and blocks until it completes.
     *
     * @param messages the running conversation so far
     * @return the model's response
     */
    public InferenceTurn submitInferenceTurn(final List<ConversationMessage> messages) {
        var responseJson = this.submit(LlmRequestKind.INFERENCE_TURN, this.toJson(messages));
        return this.fromJson(responseJson, InferenceTurn.class);
    }

    /**
     * Submits one prompt decomposition and blocks until it completes.
     *
     * @param request the original request text, verbatim, plus any revision guidance
     * @return the model's plan, or an unreachable outcome the caller decides how to handle
     */
    public DecompositionOutcome submitDecomposition(final DecompositionRequest request) {
        var responseJson = this.submit(LlmRequestKind.DECOMPOSITION, this.toJson(request));
        return this.fromJson(responseJson, DecompositionOutcome.class);
    }

    /**
     * Submits one attempt evaluation and blocks until it completes.
     *
     * @param request the task and attempt outcome to judge
     * @return the evaluator's verdict, or an unreachable outcome the caller decides how to handle
     */
    public EvaluationOutcome submitEvaluation(final EvaluationRequest request) {
        var responseJson = this.submit(LlmRequestKind.EVALUATION, this.toJson(request));
        return this.fromJson(responseJson, EvaluationOutcome.class);
    }

    /**
     * Submits one reattempt decision and blocks until it completes.
     *
     * @param request the failed task's context
     * @return the reattempt decision, or an unreachable outcome the caller decides how to handle
     */
    public ReattemptDecisionOutcome submitReattemptDecision(
            final ReattemptDecisionRequest request) {
        var responseJson = this.submit(LlmRequestKind.REATTEMPT_DECISION, this.toJson(request));
        return this.fromJson(responseJson, ReattemptDecisionOutcome.class);
    }

    /**
     * Submits one task-plan refinement critique and blocks until it completes.
     *
     * @param request the plan (and the original request it should serve) to critique
     * @return the critique, or an unreachable outcome the caller decides how to handle
     */
    public TaskPlanRefinementOutcome submitTaskPlanRefinement(
            final TaskPlanRefinementRequest request) {
        var responseJson = this.submit(LlmRequestKind.TASK_PLAN_REFINEMENT, this.toJson(request));
        return this.fromJson(responseJson, TaskPlanRefinementOutcome.class);
    }

    private String submit(final LlmRequestKind kind, final String requestJson) {
        var messageId = this.enqueue(kind, requestJson);
        return this.awaitResult(messageId);
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
     * Whether the queue has gone quiet for too long -- nothing claimed anywhere for
     * {@code stallTimeoutSeconds}, counted from the later of the last claim and when this
     * particular wait began.
     *
     * <p>Never from a claim older than the wait itself: the queue's last claim is routinely
     * minutes old for a perfectly healthy reason -- one long request (a slow generation) holding
     * the single worker the whole time. A caller that enqueues right as that request finishes
     * must get the full stall window to be claimed, not be judged stalled on its very first poll
     * because the previous claim happened before it ever arrived.</p>
     *
     * <p>Only meaningful while the awaited message is still {@code PENDING}; see the class-level
     * Javadoc for why a {@code CLAIMED} message must never be judged by this check.</p>
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
