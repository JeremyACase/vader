package org.vader.core.server.service.agent.orchestrator;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.model.vader.entity.OutboxMessageStatus;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskAttemptStatus;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.common.model.vader.entity.TaskUpdateType;
import org.vader.common.model.vader.entity.WorkflowEntity;
import org.vader.common.model.vader.entity.WorkflowStatus;
import org.vader.core.server.models.TaskAttemptSettledEvent;
import org.vader.core.server.models.WorkflowDecomposedEvent;
import org.vader.core.server.repository.TaskAttemptRepository;
import org.vader.core.server.repository.TaskAttemptReviewOutboxMessageRepository;
import org.vader.core.server.repository.TaskUpdateRepository;
import org.vader.core.server.repository.WorkflowRepository;
import org.vader.core.server.service.agent.WorkflowSynthesisService;
import org.vader.core.server.service.io.TaskAssignmentOutbox;
import org.vader.core.server.service.io.TaskAttemptReviewOutbox;

/**
 * Walks a workflow's task graph and drives it forward: dispatches every task whose dependencies
 * are all satisfied, hands a newly-terminal attempt off for review once it has no verdict yet,
 * propagates permanent failure to any task that transitively depends on one, and closes out the
 * workflow once every task has reached a terminal outcome.
 *
 * <p>Deliberately does not decide retries itself: once an attempt is terminal, this only ever
 * enqueues it for review ({@link TaskAttemptReviewOutbox}), whose pipeline
 * ({@code TaskAttemptReviewService} -- an evaluator's verdict, and on failure, the orchestrator's
 * own reattempt decision) is what actually dispatches a fresh attempt when one is warranted, via
 * {@link #dispatch}. That split exists because review involves LLM calls that must never block
 * this class's own thread -- see {@link TaskGraphSchedulerListener}'s javadoc for why that thread
 * is shared across every workflow on this replica.</p>
 *
 * <p>Driven by {@link TaskGraphSchedulerListener}, which reacts to {@link WorkflowDecomposedEvent}
 * and {@link TaskAttemptSettledEvent} and calls {@link #evaluate}. That call happens from a
 * <em>different</em> bean deliberately -- Spring refuses to register a method annotated both
 * {@code @Transactional} and {@code @TransactionalEventListener}, so the transactional boundary
 * has to live here, one hop away from the listener itself, the same split
 * {@code QueueMessageProcessor} uses opposite {@code AbstractInbox}.</p>
 */
@Service
public class TaskGraphScheduler {

    private static final Logger logger = LoggerFactory.getLogger(TaskGraphScheduler.class);

    private static final List<TaskUpdateType> VERDICT_TYPES =
        List.of(TaskUpdateType.COMPLETED, TaskUpdateType.FAILED, TaskUpdateType.TIMED_OUT);

    private static final List<OutboxMessageStatus> OPEN_REVIEW_STATUSES =
        List.of(OutboxMessageStatus.PENDING, OutboxMessageStatus.CLAIMED);

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private TaskAttemptRepository taskAttemptRepository;

    @Autowired
    private TaskUpdateRepository taskUpdateRepository;

    @Autowired
    private TaskAssignmentOutbox taskAssignmentOutbox;

    @Autowired
    private TaskAttemptReviewOutbox taskAttemptReviewOutbox;

    @Autowired
    private TaskAttemptReviewOutboxMessageRepository taskAttemptReviewOutboxMessageRepository;

    @Autowired
    private WorkflowSynthesisService workflowSynthesisService;

    /**
     * Re-derives every task's progress, dispatches whatever is now ready, enqueues review for
     * whatever just went terminal, and closes out the workflow if everything has settled.
     *
     * @param workflowId the workflow to evaluate
     */
    @Transactional
    public void evaluate(final String workflowId) {
        var workflow = this.workflowRepository.findById(workflowId).orElseThrow();
        if (isTerminal(workflow.getStatus())) {
            return;
        }

        var tasks = workflow.getTaskPlan().getTaskGraph().getTasks();
        var progress = new LinkedHashMap<String, TaskProgress>();
        tasks.forEach(task -> progress.put(task.getId(), this.computeProgress(task)));
        this.propagateBlocked(tasks, progress);

        tasks.forEach(task -> this.maybeDispatch(task, progress));
        tasks.forEach(task -> this.maybeEnqueueReview(task, progress));
        this.maybeCompleteWorkflow(workflow, progress);
    }

    /**
     * Dispatches a fresh attempt at a task -- the one place a {@code TaskAttemptEntity} is
     * created. Called internally for a task's very first attempt, and by
     * {@code OrchestratorAgentService} for every retry it approves, so a task's dispatch history
     * is uniform regardless of which caller decided it was warranted.
     *
     * @param task the task to attempt
     * @param attemptNumber the attempt number to dispatch as
     */
    @Transactional
    public void dispatch(final TaskEntity task, final int attemptNumber) {
        var attempt = new TaskAttemptEntity();
        attempt.setTask(task);
        attempt.setAttemptNumber(attemptNumber);
        attempt.setStatus(TaskAttemptStatus.PENDING);
        var saved = this.taskAttemptRepository.save(attempt);
        logger.info("Dispatching task {} (attempt {}) as assignment {}",
            task.getId(), attemptNumber, saved.getId());
        this.taskAssignmentOutbox.enqueue(saved);
    }

    /**
     * {@code AWAITING_LLM} is deliberately not terminal: a workflow paused on one task's review
     * must keep dispatching and reviewing everything else that doesn't depend on it.
     */
    private static boolean isTerminal(final WorkflowStatus status) {
        return status == WorkflowStatus.SUCCEEDED || status == WorkflowStatus.FAILED;
    }

    private TaskProgress computeProgress(final TaskEntity task) {
        var latest = this.taskAttemptRepository.findFirstByTaskIdOrderByAttemptNumberDesc(
            task.getId());
        return latest.map(this::progressFrom)
            .orElse(new TaskProgress(TaskState.READY_CANDIDATE, null));
    }

    private TaskProgress progressFrom(final TaskAttemptEntity attempt) {
        return isOpen(attempt.getStatus())
            ? new TaskProgress(TaskState.IN_FLIGHT, null)
            : this.progressFromVerdict(attempt);
    }

    private static boolean isOpen(final TaskAttemptStatus status) {
        return status == TaskAttemptStatus.PENDING
            || status == TaskAttemptStatus.DISPATCHED
            || status == TaskAttemptStatus.RUNNING;
    }

    private TaskProgress progressFromVerdict(final TaskAttemptEntity attempt) {
        var verdict = this.taskUpdateRepository
            .findFirstByTaskAttemptIdAndTypeInOrderByCreatedAtDesc(attempt.getId(), VERDICT_TYPES);
        return verdict
            .map(update -> new TaskProgress(this.stateFor(update.getType()), attempt))
            .orElse(new TaskProgress(TaskState.UNDER_REVIEW, attempt));
    }

    private TaskState stateFor(final TaskUpdateType verdictType) {
        return verdictType == TaskUpdateType.COMPLETED
            ? TaskState.DONE_SUCCEEDED
            : TaskState.DONE_FAILED;
    }

    private void propagateBlocked(
        final Set<TaskEntity> tasks,
        final Map<String, TaskProgress> progress) {

        var changed = new AtomicBoolean(true);
        while (changed.get()) {
            changed.set(false);
            tasks.forEach(task -> this.maybeMarkBlocked(task, progress, changed));
        }
    }

    private void maybeMarkBlocked(
        final TaskEntity task,
        final Map<String, TaskProgress> progress,
        final AtomicBoolean changed) {

        var current = progress.get(task.getId());
        var alreadyTerminal = current.state() == TaskState.DONE_FAILED
            || current.state() == TaskState.DONE_SUCCEEDED;
        var hasFailedDependency = task.getDependsOn().stream()
            .anyMatch(dependency -> progress.get(dependency.getId()).state()
                == TaskState.DONE_FAILED);

        if (!alreadyTerminal && hasFailedDependency) {
            progress.put(task.getId(), new TaskProgress(TaskState.DONE_FAILED, null));
            changed.set(true);
        }
    }

    private void maybeDispatch(final TaskEntity task, final Map<String, TaskProgress> progress) {
        var current = progress.get(task.getId());
        if (current.state() != TaskState.READY_CANDIDATE) {
            return;
        }
        var dependenciesSatisfied = task.getDependsOn().stream()
            .allMatch(dependency -> progress.get(dependency.getId()).state()
                == TaskState.DONE_SUCCEEDED);
        if (!dependenciesSatisfied) {
            return;
        }
        this.dispatch(task, 1);
    }

    private void maybeEnqueueReview(
        final TaskEntity task, final Map<String, TaskProgress> progress) {
        var current = progress.get(task.getId());
        if (current.state() != TaskState.UNDER_REVIEW) {
            return;
        }
        var attempt = current.settledAttempt();
        if (!this.taskAttemptReviewOutboxMessageRepository
                .existsByTaskAttemptIdAndStatusIn(attempt.getId(), OPEN_REVIEW_STATUSES)) {
            this.taskAttemptReviewOutbox.enqueue(attempt);
        }
    }

    private void maybeCompleteWorkflow(
        final WorkflowEntity workflow,
        final Map<String, TaskProgress> progress) {

        var allSettled = progress.values().stream()
            .allMatch(p -> p.state() == TaskState.DONE_SUCCEEDED
                || p.state() == TaskState.DONE_FAILED);
        if (!allSettled) {
            return;
        }

        var allSucceeded = progress.values().stream()
            .allMatch(p -> p.state() == TaskState.DONE_SUCCEEDED);
        workflow.setStatus(allSucceeded ? WorkflowStatus.SUCCEEDED : WorkflowStatus.FAILED);
        workflow.setCompletedAt(OffsetDateTime.now());
        workflow.setResult(this.workflowSynthesisService.synthesize(workflow));
        this.workflowRepository.save(workflow);
        logger.info("Workflow {} completed with status {}",
            workflow.getId(), workflow.getStatus());
    }

    /** A task's derived scheduling state -- never persisted, recomputed on every evaluation. */
    private enum TaskState {
        /** Never attempted. */
        READY_CANDIDATE,
        /** An attempt is currently pending, dispatched, or running. */
        IN_FLIGHT,
        /** The latest attempt is terminal but has no verdict yet; awaiting review. */
        UNDER_REVIEW,
        /** The latest attempt was verdicted a success. */
        DONE_SUCCEEDED,
        /**
         * The latest attempt was verdicted a failure and no newer attempt exists -- if a retry
         * had been approved, the review pipeline would already have dispatched one, making it the
         * latest attempt instead. Also reached when a dependency permanently failed.
         */
        DONE_FAILED
    }

    /**
     * A task's derived state plus, when relevant, the specific attempt that state was derived
     * from.
     *
     * @param state the derived scheduling state
     * @param settledAttempt the attempt {@code state} was derived from, when {@code state} is
     *     {@code UNDER_REVIEW}; {@code null} otherwise
     */
    private record TaskProgress(TaskState state, TaskAttemptEntity settledAttempt) {
    }
}
