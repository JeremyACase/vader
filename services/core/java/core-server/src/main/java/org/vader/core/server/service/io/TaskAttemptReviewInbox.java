package org.vader.core.server.service.io;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.vader.common.model.vader.entity.OutboxMessageStatus;
import org.vader.common.model.vader.entity.TaskAttemptReviewOutboxMessageEntity;
import org.vader.core.server.models.OutboxMessageEnqueuedEvent;
import org.vader.core.server.repository.OutboxMessageRepository;
import org.vader.core.server.repository.TaskAttemptReviewOutboxMessageRepository;
import org.vader.core.server.service.agent.TaskAttemptReviewRetryService;
import org.vader.core.server.service.agent.TaskAttemptReviewService;
import org.vader.core.server.service.llm.LlmOutageClassifier;

/**
 * Inbox for attempt review: pops pending review messages and runs
 * {@link TaskAttemptReviewService#review} on each -- an evaluator's verdict, and on failure, the
 * orchestrator's reattempt decision -- off {@code TaskGraphScheduler}'s own thread, since review
 * involves LLM calls that must not block workflow-progress bookkeeping.
 *
 * <p>{@code handle} only ever touches the claimed message's id (safe to read outside a session,
 * same as every other lazy-proxy field access in this package's inboxes) -- the actual review
 * happens inside {@link TaskAttemptReviewService}'s own transactional method, which re-fetches
 * fresh by id.</p>
 *
 * <p>A review that fails because the LLM is unavailable is not marked {@code FAILED} -- that
 * would leave its task with no verdict and its workflow stuck in {@code RUNNING} forever. It is
 * handed to {@link TaskAttemptReviewRetryService}, which puts it back on the queue for a later
 * retry and marks the workflow {@code AWAITING_LLM}; {@link #pop} then only claims reviews whose
 * retry time has come. Any other failure is still marked {@code FAILED} as before.</p>
 */
@Service
public class TaskAttemptReviewInbox extends AbstractInbox<TaskAttemptReviewOutboxMessageEntity> {

    private static final String TASK_ATTEMPT_REVIEW = "TaskAttemptReview";

    @Autowired
    private TaskAttemptReviewOutboxMessageRepository messageRepository;

    // Lazy: this inbox is itself one of BackpressureRegistry's queues, and TaskAttemptReviewService
    // depends (lazily, for the same reason) on agent services that -- in "local" mode -- drag in
    // the whole Spring AI tool-calling graph, closing a cycle back through this bean. Same
    // reasoning as ClientPromptInbox's lazy OrchestratorAgentService.
    @Autowired
    @Lazy
    private TaskAttemptReviewService reviewService;

    @Autowired
    private TaskAttemptReviewRetryService retryService;

    @Autowired
    @Qualifier("taskAttemptReviewInboxExecutor")
    private TaskExecutor executor;

    @Value("${vader.inbox.task-attempt-review.max-concurrency:1}")
    private int maxConcurrency;

    @Override
    public String queuedModelType() {
        return TASK_ATTEMPT_REVIEW;
    }

    @Override
    public int maxOpenMessages() {
        return this.maxConcurrency;
    }

    @Override
    protected OutboxMessageRepository<TaskAttemptReviewOutboxMessageEntity> repository() {
        return this.messageRepository;
    }

    /**
     * Claims the oldest pending review that is due -- skipping any deferred during an LLM outage
     * whose retry time has not yet come.
     *
     * @return the claimed message, or empty if nothing is due
     */
    @Override
    public Optional<TaskAttemptReviewOutboxMessageEntity> pop() {
        return this.processor().claim(this.messageRepository, () -> this.messageRepository
            .findDue(OutboxMessageStatus.PENDING, OffsetDateTime.now(), PageRequest.of(0, 1))
            .stream()
            .findFirst());
    }

    @Override
    protected void handle(final TaskAttemptReviewOutboxMessageEntity message) {
        this.reviewService.review(message.getTaskAttempt().getId());
    }

    @Override
    protected void onHandleFailure(
            final TaskAttemptReviewOutboxMessageEntity message, final RuntimeException failure) {
        if (LlmOutageClassifier.isOutage(failure)) {
            this.retryService.deferForLlmOutage(message.getId(), failure.getMessage());
        } else {
            super.onHandleFailure(message, failure);
        }
    }

    /**
     * Scheduled safety-net drain.
     */
    @Scheduled(fixedDelayString = "${vader.inbox.task-attempt-review.poll-interval-ms:1000}")
    public void scheduledDrain() {
        this.drain();
    }

    /**
     * Drains immediately once a newly enqueued review message has committed.
     *
     * @param event the enqueue notification
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEnqueued(final OutboxMessageEnqueuedEvent event) {
        if (TASK_ATTEMPT_REVIEW.equals(event.modelType())) {
            this.executor.execute(this::drain);
        }
    }
}
