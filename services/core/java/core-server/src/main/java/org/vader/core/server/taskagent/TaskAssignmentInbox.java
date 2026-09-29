package org.vader.core.server.taskagent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.vader.common.model.vader.entity.TaskAssignmentOutboxMessageEntity;
import org.vader.core.server.messaging.AbstractInbox;
import org.vader.core.server.messaging.OutboxMessageEnqueuedEvent;
import org.vader.core.server.messaging.OutboxMessageRepository;
import org.vader.core.server.taskagent.harness.AgentHarnessOperator;

/**
 * Inbox for task assignments: pops pending assignment messages and asks the agent-harness
 * operator to create a Job for each, via {@link TaskAttemptLifecycleService}.
 *
 * <p>{@code handle} only ever touches the claimed message's id (safe to read outside a session,
 * same as every other lazy-proxy field access in this package's inboxes) -- the actual
 * dispatch and status mutation happen inside {@link TaskAttemptLifecycleService}'s own
 * transactional methods, which re-fetch fresh by id.</p>
 */
@Service
public class TaskAssignmentInbox extends AbstractInbox<TaskAssignmentOutboxMessageEntity> {

    private static final String TASK_ASSIGNMENT = "TaskAssignment";

    @Autowired
    private TaskAssignmentOutboxMessageRepository messageRepository;

    // Lazy: this inbox is itself one of BackpressureRegistry's queues, and the lifecycle service
    // reaches the operator and sandbox beans, which lead back to BackpressureRegistry. Same
    // reasoning as ClientPromptInbox's own lazy OrchestratorAgentService.
    @Autowired
    @Lazy
    private TaskAttemptLifecycleService lifecycleService;

    @Autowired(required = false)
    private AgentHarnessOperator operator;

    @Autowired
    @Qualifier("taskAssignmentInboxExecutor")
    private TaskExecutor executor;

    @Value("${vader.inbox.task-assignment.max-concurrency:5}")
    private int maxConcurrency;

    @Override
    public String queuedModelType() {
        return TASK_ASSIGNMENT;
    }

    @Override
    public int maxOpenMessages() {
        return this.maxConcurrency;
    }

    @Override
    protected OutboxMessageRepository<TaskAssignmentOutboxMessageEntity> repository() {
        return this.messageRepository;
    }

    @Override
    protected void handle(final TaskAssignmentOutboxMessageEntity message) {
        var assignmentId = message.getTaskAttempt().getId();

        if (this.operator == null) {
            var reason = "The agent-harness operator is disabled.";
            this.lifecycleService.markDispatchFailed(assignmentId, reason);
            throw new IllegalStateException(reason);
        }

        var spec = this.lifecycleService.specFor(assignmentId);
        try {
            this.operator.reconcile(spec);
        } catch (RuntimeException e) {
            this.lifecycleService.markDispatchFailed(
                assignmentId, "Dispatch failed: " + e.getMessage());
            throw e;
        }
        this.lifecycleService.markDispatched(assignmentId);
    }

    /**
     * Scheduled safety-net drain.
     */
    @Scheduled(fixedDelayString = "${vader.inbox.task-assignment.poll-interval-ms:1000}")
    public void scheduledDrain() {
        this.drain();
    }

    /**
     * Drains immediately once a newly enqueued assignment message has committed.
     *
     * @param event the enqueue notification
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEnqueued(final OutboxMessageEnqueuedEvent event) {
        if (TASK_ASSIGNMENT.equals(event.modelType())) {
            this.executor.execute(this::drain);
        }
    }
}
