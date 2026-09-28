package org.vader.core.server.service.agent.orchestrator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.vader.core.server.models.TaskAttemptSettledEvent;
import org.vader.core.server.models.WorkflowDecomposedEvent;

/**
 * Bridges workflow/task-attempt domain events to {@link TaskGraphScheduler#evaluate}.
 *
 * <p>Kept separate from {@link TaskGraphScheduler} itself: Spring will not register a method
 * annotated both {@code @Transactional} and {@code @TransactionalEventListener}, so the actual
 * (transactional) evaluation has to live on a different bean than the listener that triggers
 * it -- calling {@code this.evaluate(...)} from within the same class would also silently skip
 * the transactional proxy via self-invocation, so this split solves both problems at once.</p>
 *
 * <p>Critically, {@link #scheduler}'s {@code evaluate} is handed off to a dedicated executor
 * rather than called directly. An {@code AFTER_COMMIT} listener runs synchronously on the
 * thread whose transaction just committed, and Spring has not yet cleared that thread's bound
 * {@code EntityManager} at that point -- calling a {@code @Transactional} method straight from
 * here would make it "participate" in that already-committed, stale transaction instead of
 * opening a fresh one, silently losing everything {@code evaluate} writes. Hopping to a new
 * thread first is what actually starts a clean transaction; {@code ClientPromptInbox} and
 * {@code TaskAssignmentInbox} use the same handoff for the identical reason.</p>
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
