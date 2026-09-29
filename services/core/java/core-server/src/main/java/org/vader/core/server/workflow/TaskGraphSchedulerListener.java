package org.vader.core.server.workflow;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges workflow/task-attempt domain events to {@link TaskGraphScheduler#evaluate}.
 *
 * <p>A separate bean from {@link TaskGraphScheduler} because Spring rejects a method that is both
 * {@code @Transactional} and a {@code @TransactionalEventListener}, and a self-call would skip the
 * transactional proxy anyway.</p>
 *
 * <p>Hands {@code evaluate} to a dedicated executor rather than calling it directly: an
 * {@code AFTER_COMMIT} listener runs on the committing thread, whose stale {@code EntityManager}
 * is still bound, so a direct {@code @Transactional} call would join that finished transaction
 * and lose its writes. {@code ClientPromptInbox} and {@code TaskAssignmentInbox} hop threads for
 * the same reason.</p>
 */
@Service
public class TaskGraphSchedulerListener {

    @Autowired
    private TaskGraphScheduler scheduler;

    @Autowired
    @Qualifier("taskGraphSchedulerExecutor")
    private TaskExecutor executor;

    /**
     * Dispatches a newly-decomposed workflow's initial ready set.
     *
     * @param event the decomposition notification
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWorkflowDecomposed(final WorkflowDecomposedEvent event) {
        this.executor.execute(() -> this.scheduler.evaluate(event.workflowId()));
    }

    /**
     * Re-evaluates a workflow after one of its tasks settles.
     *
     * @param event the settlement notification
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskAttemptSettled(final TaskAttemptSettledEvent event) {
        this.executor.execute(() -> this.scheduler.evaluate(event.workflowId()));
    }
}
