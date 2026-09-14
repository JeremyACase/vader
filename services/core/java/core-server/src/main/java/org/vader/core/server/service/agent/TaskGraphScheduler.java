package org.vader.core.server.service.agent;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskAttemptStatus;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.common.model.vader.entity.WorkflowEntity;
import org.vader.common.model.vader.entity.WorkflowStatus;
import org.vader.core.server.models.TaskAttemptSettledEvent;
import org.vader.core.server.models.WorkflowDecomposedEvent;
import org.vader.core.server.repository.TaskAttemptRepository;
import org.vader.core.server.repository.WorkflowRepository;
import org.vader.core.server.service.io.TaskAssignmentOutbox;

/**
 * Walks a workflow's task graph and drives it forward: dispatches every task whose dependencies
 * are all satisfied, retries a task that failed (up to a cap) by dispatching a fresh attempt,
 * propagates permanent failure to any task that transitively depends on one, and closes out the
 * workflow once every task has reached a terminal outcome.
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

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private TaskAttemptRepository taskAttemptRepository;

    @Autowired
    private TaskAssignmentOutbox taskAssignmentOutbox;

    @Autowired
    private WorkflowSynthesisService workflowSynthesisService;

    @Value("${vader.agent-harness.max-attempts-per-task:3}")
    private int maxAttemptsPerTask;

    /**
     * Re-derives every task's progress, dispatches whatever is now ready, and closes out the
     * workflow if everything has settled.
     *
     * @param workflowId the workflow to evaluate
     */
    @Transactional
    public void evaluate(final String workflowId) {
        var workflow = this.workflowRepository.findById(workflowId).orElseThrow();
        if (workflow.getStatus() != WorkflowStatus.RUNNING) {
            return;
        }

        var tasks = workflow.getTaskPlan().getTaskGraph().getTasks();
        var progress = new LinkedHashMap<String, TaskProgress>();
        tasks.forEach(task -> progress.put(task.getId(), this.computeProgress(task)));
        this.propagateBlocked(tasks, progress);

        tasks.forEach(task -> this.maybeDispatch(task, progress));
        this.maybeCompleteWorkflow(workflow, progress);
    }

    private TaskProgress computeProgress(final TaskEntity task) {
        var latest = this.taskAttemptRepository.findFirstByTaskIdOrderByAttemptNumberDesc(
            task.getId());
        if (latest.isEmpty()) {
            return new TaskProgress(TaskState.READY_CANDIDATE, 1);
        }
        return this.progressFrom(latest.get());
    }

    private TaskProgress progressFrom(final TaskAttemptEntity attempt) {
        return switch (attempt.getStatus()) {
            case SUCCEEDED -> new TaskProgress(TaskState.DONE_SUCCEEDED, 0);
            case FAILED, TIMED_OUT, STALLED -> attempt.getAttemptNumber() < this.maxAttemptsPerTask
                ? new TaskProgress(TaskState.READY_CANDIDATE, attempt.getAttemptNumber() + 1)
                : new TaskProgress(TaskState.DONE_FAILED, 0);
            case PENDING, DISPATCHED, RUNNING -> new TaskProgress(TaskState.IN_FLIGHT, 0);
        };
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
            progress.put(task.getId(), new TaskProgress(TaskState.DONE_FAILED, 0));
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
        this.dispatch(task, current.nextAttemptNumber());
    }

    private void dispatch(final TaskEntity task, final int attemptNumber) {
        var attempt = new TaskAttemptEntity();
        attempt.setTask(task);
        attempt.setAttemptNumber(attemptNumber);
        attempt.setStatus(TaskAttemptStatus.PENDING);
        var saved = this.taskAttemptRepository.save(attempt);
        logger.info("Dispatching task {} (attempt {}) as assignment {}",
            task.getId(), attemptNumber, saved.getId());
        this.taskAssignmentOutbox.enqueue(saved);
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
        /** Never attempted, or its last attempt failed with retries remaining. */
        READY_CANDIDATE,
        /** An attempt is currently pending, dispatched, or running. */
        IN_FLIGHT,
        /** The latest (or only) attempt succeeded. */
        DONE_SUCCEEDED,
        /** Every attempt is exhausted, or a dependency permanently failed. */
        DONE_FAILED
    }

    /**
     * A task's derived state plus the attempt number it should dispatch as next, when ready.
     *
     * @param state the derived scheduling state
     * @param nextAttemptNumber meaningful only when {@code state} is {@code READY_CANDIDATE}
     */
    private record TaskProgress(TaskState state, int nextAttemptNumber) {
    }
}
