package org.vader.core.server.workflow;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import org.vader.core.server.review.TaskAttemptReviewOutbox;
import org.vader.core.server.review.TaskAttemptReviewOutboxMessageRepository;
import org.vader.core.server.review.TaskDecompositionSaga;
import org.vader.core.server.taskagent.TaskAssignmentOutbox;

/**
 * Walks a workflow's task graph and drives it forward: dispatches every task whose dependencies
 * are all satisfied, hands a newly-terminal attempt off for review once it has no verdict yet,
 * propagates permanent failure to any task that transitively depends on one, and closes out the
 * workflow once every task has reached a terminal outcome.
 *
 * <p>Never decides retries itself: a terminal attempt is enqueued for review
 * ({@link TaskAttemptReviewOutbox}), whose pipeline calls {@link #dispatch} when a fresh attempt
 * is warranted. Review makes LLM calls, which must never block this thread -- it is shared by
 * every workflow on this replica.</p>
 *
 * <p>Includes runtime subtasks: a task whose latest verdict is {@code DECOMPOSED} waits on them,
 * and this is where {@link TaskDecompositionSaga} rolls it up or fails it -- deterministic, so
 * safe on this thread.</p>
 *
 * <p>{@link TaskGraphSchedulerListener} reacts to {@link WorkflowDecomposedEvent} and
 * {@link TaskAttemptSettledEvent} and calls {@link #evaluate} on this separate bean, because a
 * self-call would skip the transactional proxy.</p>
 */
@Service
public class TaskGraphScheduler {

    private static final Logger logger = LoggerFactory.getLogger(TaskGraphScheduler.class);

    private static final List<TaskUpdateType> VERDICT_TYPES = List.of(
        TaskUpdateType.COMPLETED, TaskUpdateType.FAILED, TaskUpdateType.TIMED_OUT,
        TaskUpdateType.DECOMPOSED);

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

    @Autowired
    private TaskDecompositionSaga taskDecompositionSaga;

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

        var tasks = allTasks(workflow.getTaskPlan().getTaskGraph().getTasks());
        var progress = new LinkedHashMap<String, TaskProgress>();
        tasks.forEach(task -> progress.put(task.getId(), this.computeProgress(task)));
        // Deepest first, so a subtask that was itself decomposed settles before its parent looks.
        tasks.reversed().forEach(task -> this.maybeSettleDecomposed(task, progress));
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

    /**
     * Every schedulable task in the graph -- the roots plus every subtask spawned at runtime by a
     * decomposition -- each parent before its subtasks. Subtasks a plan itself nested under a
     * task (no spawning attempt) are left out, exactly as they were before runtime decomposition
     * existed: the planner's own tasks are the unit of work there.
     */
    private static List<TaskEntity> allTasks(final Collection<TaskEntity> tasks) {
        var all = new ArrayList<TaskEntity>();
        tasks.forEach(task -> {
            all.add(task);
            all.addAll(allTasks(runtimeSubtasksOf(task)));
        });
        return all;
    }

    private static List<TaskEntity> runtimeSubtasksOf(final TaskEntity task) {
        return task.getSubTasks().stream()
            .filter(subtask -> Objects.nonNull(subtask.getSpawnedByAttempt()))
            .toList();
    }

    /**
     * Rolls up or fails a decomposed task once its subtasks have settled, then re-derives its
     * progress from the verdict just recorded so its dependents react in this same pass.
     */
    private void maybeSettleDecomposed(
            final TaskEntity task, final Map<String, TaskProgress> progress) {
        var current = progress.get(task.getId());
        if (current.state() != TaskState.AWAITING_SUBTASKS) {
            return;
        }
        var attempt = current.settledAttempt();
        var subtasks = this.taskDecompositionSaga.subtasksOf(attempt);
        var failed = subtasks.stream()
            .filter(subtask -> progress.get(subtask.getId()).state() == TaskState.DONE_FAILED)
            .findFirst();
        // A decomposition always spawns at least one subtask, so finding none means they failed
        // to load -- never that there was nothing left to do.
        var allSucceeded = !subtasks.isEmpty() && subtasks.stream()
            .allMatch(subtask -> progress.get(subtask.getId()).state()
                == TaskState.DONE_SUCCEEDED);
        failed.ifPresent(subtask -> this.taskDecompositionSaga.compensate(attempt, subtask));
        if (failed.isEmpty() && allSucceeded) {
            this.taskDecompositionSaga.rollUp(attempt);
        }
        progress.put(task.getId(), this.computeProgress(task));
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
        return switch (verdictType) {
            case COMPLETED -> TaskState.DONE_SUCCEEDED;
            case DECOMPOSED -> TaskState.AWAITING_SUBTASKS;
            default -> TaskState.DONE_FAILED;
        };
    }

    private void propagateBlocked(
        final List<TaskEntity> tasks,
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
        /**
         * The latest attempt was decomposed: its remaining work is running as subtasks, and the
         * task completes or fails once they settle.
         */
        AWAITING_SUBTASKS,
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
     * @param settledAttempt the attempt {@code state} was derived from, when the task has a
     *     settled attempt; {@code null} otherwise
     */
    private record TaskProgress(TaskState state, TaskAttemptEntity settledAttempt) {
    }
}
