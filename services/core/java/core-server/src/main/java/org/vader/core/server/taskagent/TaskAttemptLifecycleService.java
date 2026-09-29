package org.vader.core.server.taskagent;

import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskAttemptStatus;
import org.vader.common.model.vader.entity.TaskUpdateAuthor;
import org.vader.common.model.vader.entity.TaskUpdateType;
import org.vader.core.server.taskagent.harness.AgentHarnessSpec;
import org.vader.core.server.taskagent.model.AssignmentResponse;
import org.vader.core.server.taskagent.model.HeartbeatRequest;
import org.vader.core.server.taskagent.model.ResultRequest;
import org.vader.core.server.workflow.TaskAttemptRepository;
import org.vader.core.server.workflow.TaskAttemptSettledEvent;
import org.vader.core.server.workflow.TaskUpdateService;

/**
 * Owns every status transition a {@code TaskAttempt} goes through, from dispatch to a terminal
 * outcome -- the durable audit trail behind "log everything from prompt to workflow finished."
 * Backs {@code TaskAssignmentInbox} (dispatch bookkeeping), {@code TaskAttemptReaper} and the
 * status half of {@code AgentAssignmentController}.
 *
 * <p>Every public method re-fetches the attempt fresh by id rather than accepting a caller-held
 * reference, the same reasoning {@code QueueMessageProcessor} documents: each call is its own
 * independent, short transaction.</p>
 */
@Service
public class TaskAttemptLifecycleService {

    @Autowired
    private TaskAttemptRepository taskAttemptRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private TaskUpdateService taskUpdateService;

    @Autowired
    private AssignmentContextBuilder assignmentContextBuilder;

    @Value("${vader.agent-harness.max-turns:20}")
    private int maxTurns;

    @Value("${vader.agent-harness.max-tokens:200000}")
    private long maxTokens;

    @Value("${vader.agent-harness.deadline-seconds:600}")
    private long deadlineSeconds;

    /**
     * Resolves the task/assignment identity a Job manifest needs, for {@code TaskAssignmentInbox}
     * to dispatch.
     *
     * @param assignmentId the attempt id about to be dispatched
     * @return the resolved spec
     */
    @Transactional
    public AgentHarnessSpec specFor(final String assignmentId) {
        var attempt = this.require(assignmentId);
        return new AgentHarnessSpec(attempt.getTask().getId(), attempt.getId());
    }

    /**
     * Records that a harness Job was successfully created for this attempt.
     *
     * @param assignmentId the dispatched attempt id
     */
    @Transactional
    public void markDispatched(final String assignmentId) {
        var attempt = this.require(assignmentId);
        attempt.setStatus(TaskAttemptStatus.DISPATCHED);
        attempt.setDispatchedAt(OffsetDateTime.now());
        this.taskAttemptRepository.save(attempt);
    }

    /**
     * Records that dispatch itself failed -- no Job was ever created, so this attempt can never
     * report back on its own. Settles it as {@code FAILED} immediately.
     *
     * @param assignmentId the attempt id that failed to dispatch
     * @param reason why dispatch failed
     */
    @Transactional
    public void markDispatchFailed(final String assignmentId, final String reason) {
        var attempt = this.require(assignmentId);
        this.settle(attempt, TaskAttemptStatus.FAILED, null, reason);
    }

    /**
     * Fetches the work order for an assignment, marking the attempt {@code RUNNING} on first
     * contact.
     *
     * @param assignmentId the calling harness's assignment id
     * @return the work order
     */
    @Transactional
    public AssignmentResponse fetchAssignment(final String assignmentId) {
        var attempt = this.requireOpen(assignmentId);

        if (attempt.getStatus() != TaskAttemptStatus.RUNNING) {
            attempt.setStatus(TaskAttemptStatus.RUNNING);
            attempt.setStartedAt(OffsetDateTime.now());
            attempt.setLastHeartbeatAt(OffsetDateTime.now());
            this.taskAttemptRepository.save(attempt);
            this.taskUpdateService.record(
                attempt.getTask(), attempt, TaskUpdateType.RUNNING,
                "Attempt " + attempt.getAttemptNumber() + " started running.",
                TaskUpdateAuthor.TASK_AGENT);
        }

        var task = attempt.getTask();
        return new AssignmentResponse(
            task.getId(),
            attempt.getId(),
            task.getDescription(),
            this.assignmentContextBuilder.build(task),
            this.maxTurns,
            this.maxTokens,
            this.deadlineSeconds);
    }

    /**
     * Records a liveness/progress report partway through a run.
     *
     * @param assignmentId the calling harness's assignment id
     * @param request the reported progress
     */
    @Transactional
    public void recordHeartbeat(final String assignmentId, final HeartbeatRequest request) {
        var attempt = this.requireOpen(assignmentId);
        attempt.setTurnsUsed(request.turnsUsed());
        attempt.setTokensUsed(request.tokensUsed());
        attempt.setLastHeartbeatAt(OffsetDateTime.now());
        this.taskAttemptRepository.save(attempt);
    }

    /**
     * Records an attempt's terminal outcome and notifies the scheduler that this task settled.
     *
     * @param assignmentId the calling harness's assignment id
     * @param request the reported outcome
     */
    @Transactional
    public void submitResult(final String assignmentId, final ResultRequest request) {
        var attempt = this.requireOpen(assignmentId);
        this.settle(attempt, request.status(), request.output(), request.failureReason());
    }

    /**
     * Fetches an attempt that is still allowed to do work, joining the caller's transaction.
     *
     * @param assignmentId the calling harness's assignment id
     * @return the attempt
     * @throws UnknownAssignmentException if no such attempt exists
     * @throws AssignmentAlreadyTerminalException if the attempt already settled
     */
    @Transactional
    public TaskAttemptEntity requireOpen(final String assignmentId) {
        var attempt = this.require(assignmentId);
        if (isTerminal(attempt.getStatus())) {
            throw new AssignmentAlreadyTerminalException(
                "Assignment " + attempt.getId()
                    + " already reached a terminal status: " + attempt.getStatus());
        }
        return attempt;
    }

    private void settle(
        final TaskAttemptEntity attempt,
        final TaskAttemptStatus status,
        final String output,
        final String failureReason) {

        attempt.setStatus(status);
        attempt.setResult(output);
        attempt.setFailureReason(failureReason);
        attempt.setCompletedAt(OffsetDateTime.now());
        this.taskAttemptRepository.save(attempt);

        var workflowId = attempt.getTask().owningTaskGraph().getTaskPlan().getWorkflow().getId();
        this.eventPublisher.publishEvent(new TaskAttemptSettledEvent(workflowId, attempt.getId()));
    }

    private static boolean isTerminal(final TaskAttemptStatus status) {
        return status == TaskAttemptStatus.SUCCEEDED
            || status == TaskAttemptStatus.FAILED
            || status == TaskAttemptStatus.TIMED_OUT
            || status == TaskAttemptStatus.STALLED;
    }

    private TaskAttemptEntity require(final String assignmentId) {
        return this.taskAttemptRepository.findById(assignmentId)
            .orElseThrow(() -> new UnknownAssignmentException(
                "Unknown assignment: " + assignmentId));
    }
}
