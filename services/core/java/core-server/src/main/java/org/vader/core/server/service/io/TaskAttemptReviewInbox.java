package org.vader.core.server.service.io;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.vader.common.model.vader.entity.TaskAttemptReviewOutboxMessageEntity;
import org.vader.core.server.models.OutboxMessageEnqueuedEvent;
import org.vader.core.server.repository.OutboxMessageRepository;
import org.vader.core.server.repository.TaskAttemptReviewOutboxMessageRepository;
import org.vader.core.server.service.agent.TaskAttemptReviewService;

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

    @Override
    protected void handle(final TaskAttemptReviewOutboxMessageEntity message) {
        this.reviewService.review(message.getTaskAttempt().getId());
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
