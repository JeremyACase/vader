package org.vader.core.server.workflow;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskAttemptStatus;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.common.model.vader.entity.WorkflowEntity;
import org.vader.common.model.vader.entity.WorkflowStatus;
import org.vader.core.server.taskagent.TaskAttemptLifecycleService;
import org.vader.core.server.workflow.model.WorkflowCancellation;

/**
 * Stops a workflow that is still in progress, so it no longer competes for the LLM or the cluster
 * with work someone is still waiting on -- e.g. a benchmark run that has given up on it.
 *
 * <p>Marks the workflow {@code CANCELLED}, which {@link TaskGraphScheduler} treats as terminal,
 * then fails every attempt still open, which deletes its harness Job and sandbox. Reviews still
 * queued for the workflow are skipped when they come up, so no further LLM call is made for it.
 * A workflow that already settled is left as it is.</p>
 */
@Service
public class WorkflowCancellationService {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowCancellationService.class);

    private static final String CANCELLED_REASON = "Cancelled: the workflow was cancelled.";

    private static final Set<WorkflowStatus> IN_PROGRESS =
        Set.of(WorkflowStatus.RUNNING, WorkflowStatus.AWAITING_LLM);

    private static final Set<TaskAttemptStatus> OPEN_ATTEMPTS = Set.of(
        TaskAttemptStatus.PENDING, TaskAttemptStatus.DISPATCHED, TaskAttemptStatus.RUNNING);

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private TaskAttemptRepository taskAttemptRepository;

    @Autowired
    private TaskAttemptLifecycleService taskAttemptLifecycleService;

    /**
     * Cancels a workflow if it is still in progress.
     *
     * @param workflowId the workflow to cancel
     * @return its status afterwards and how many open attempts were stopped
     * @throws UnknownWorkflowException if no such workflow exists
     */
    @Transactional
    public WorkflowCancellation cancel(final String workflowId) {
        var workflow = this.workflowRepository.findById(workflowId)
            .orElseThrow(() -> new UnknownWorkflowException("Unknown workflow: " + workflowId));
        var stopped = IN_PROGRESS.contains(workflow.getStatus()) ? this.stop(workflow) : 0;
        return new WorkflowCancellation(workflowId, workflow.getStatus(), stopped);
    }

    private int stop(final WorkflowEntity workflow) {
        workflow.setStatus(WorkflowStatus.CANCELLED);
        workflow.setCompletedAt(OffsetDateTime.now());
        this.workflowRepository.save(workflow);
        var open = this.openAttempts(workflow);
        open.forEach(attempt -> this.taskAttemptLifecycleService.cancel(
            attempt.getId(), CANCELLED_REASON));
        logger.info("Workflow {} cancelled; stopped {} open attempts",
            workflow.getId(), open.size());
        return open.size();
    }

    private List<TaskAttemptEntity> openAttempts(final WorkflowEntity workflow) {
        var roots = Optional.ofNullable(workflow.getTaskPlan())
            .map(plan -> plan.getTaskGraph().getTasks())
            .orElse(Set.of());
        return everyTask(roots)
            .flatMap(task -> this.taskAttemptRepository
                .findFirstByTaskIdOrderByAttemptNumberDesc(task.getId()).stream())
            .filter(attempt -> OPEN_ATTEMPTS.contains(attempt.getStatus()))
            .toList();
    }

    private static Stream<TaskEntity> everyTask(final Collection<TaskEntity> tasks) {
        return tasks.stream()
            .flatMap(task -> Stream.concat(Stream.of(task), everyTask(task.getSubTasks())));
    }
}
