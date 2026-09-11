package org.vader.core.server.service.agent;

import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskAttemptStatus;
import org.vader.common.model.vader.entity.TaskAttemptTranscriptEntity;
import org.vader.core.exceptions.AssignmentAlreadyTerminalException;
import org.vader.core.exceptions.UnknownAssignmentException;
import org.vader.core.server.models.AgentHarnessSpec;
import org.vader.core.server.models.AssignmentResponse;
import org.vader.core.server.models.HeartbeatRequest;
import org.vader.core.server.models.InferenceTurn;
import org.vader.core.server.models.ResultRequest;
import org.vader.core.server.models.TaskAttemptSettledEvent;
import org.vader.core.server.repository.TaskAttemptRepository;
import org.vader.core.server.repository.TaskAttemptTranscriptRepository;
import org.vader.core.server.service.strategies.inference.InterfaceInferenceGatewayStrategy;

/**
 * Owns every status transition a {@code TaskAttempt} goes through, from creation through
 * dispatch to a terminal outcome -- the durable audit trail behind "log everything from prompt to
 * workflow finished." Backs both {@code TaskAssignmentInbox} (dispatch bookkeeping) and
 * {@code AgentAssignmentController} (the harness-facing control plane and inference gateway).
 *
 * <p>Every public method here re-fetches the attempt fresh by id rather than accepting a
 * caller-held reference, the same reasoning {@code QueueMessageProcessor} documents: each call is
 * its own independent, short transaction.</p>
 */
@Service
public class TaskAttemptService {

    @Autowired
    private TaskAttemptRepository taskAttemptRepository;

    @Autowired
    private TaskAttemptTranscriptRepository transcriptRepository;

    @Autowired
    private InterfaceInferenceGatewayStrategy inferenceGateway;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

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
        var attempt = this.require(assignmentId);
        this.rejectIfTerminal(attempt);

        if (attempt.getStatus() != TaskAttemptStatus.RUNNING) {
            attempt.setStatus(TaskAttemptStatus.RUNNING);
            attempt.setStartedAt(OffsetDateTime.now());
            attempt.setLastHeartbeatAt(OffsetDateTime.now());
            this.taskAttemptRepository.save(attempt);
        }

        var task = attempt.getTask();
        return new AssignmentResponse(
            task.getId(),
            attempt.getId(),
            task.getDescription(),
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
        var attempt = this.require(assignmentId);
        this.rejectIfTerminal(attempt);
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
        var attempt = this.require(assignmentId);
        this.rejectIfTerminal(attempt);
        this.settle(attempt, request.status(), request.output(), request.failureReason());
    }

    /**
     * Completes one inference turn on behalf of an assignment and logs it to the transcript --
     * this, not a direct model call, is the only path a harness has to any LLM.
     *
     * @param assignmentId the calling harness's assignment id
     * @param prompt the prompt for this turn
     * @return the model's response and its token cost
     */
    @Transactional
    public InferenceTurn recordInferenceTurn(final String assignmentId, final String prompt) {
        var attempt = this.require(assignmentId);
        this.rejectIfTerminal(attempt);

        var turn = this.inferenceGateway.complete(prompt);

        var transcript = new TaskAttemptTranscriptEntity();
        transcript.setTaskAttempt(attempt);
        transcript.setTurnIndex((int) this.transcriptRepository.countByTaskAttemptId(assignmentId));
        transcript.setPrompt(prompt);
        transcript.setResponse(turn.content());
        transcript.setTokensSpent(turn.tokensSpent());
        this.transcriptRepository.save(transcript);

        return turn;
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

        var workflowId = attempt.getTask().getTaskGraph().getTaskPlan().getWorkflow().getId();
        this.eventPublisher.publishEvent(new TaskAttemptSettledEvent(workflowId));
    }

    private void rejectIfTerminal(final TaskAttemptEntity attempt) {
        if (isTerminal(attempt.getStatus())) {
            throw new AssignmentAlreadyTerminalException(
                "Assignment " + attempt.getId()
                    + " already reached a terminal status: " + attempt.getStatus());
        }
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
