package org.vader.core.server.service.agent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskUpdateAuthor;
import org.vader.common.model.vader.entity.TaskUpdateType;
import org.vader.core.server.models.TaskAttemptSettledEvent;
import org.vader.core.server.repository.TaskAttemptRepository;
import org.vader.core.server.service.agent.evaluator.EvaluatorAgentService;
import org.vader.core.server.service.agent.orchestrator.OrchestratorAgentService;

/**
 * Coordinates review of one newly-terminal attempt: an evaluator's verdict, and -- on anything
 * short of success -- the orchestrator's own decision on whether it's worth re-attempting.
 *
 * <p>Called only from {@link org.vader.core.server.service.io.TaskAttemptReviewInbox}, which
 * drains the durable review pipeline off {@code TaskGraphScheduler}'s own thread specifically so
 * the LLM calls both steps make never block workflow-progress bookkeeping.</p>
 *
 * <p>A harness's own self-reported {@code SUCCEEDED}/{@code FAILED} genuinely needs independent
 * judgment -- an agent can report success for incomplete work, or the reverse. A
 * {@code TIMED_OUT}/{@code STALLED} attempt is different: the harness never reported back at all,
 * so there is nothing self-reported left to independently verify, and recording that verdict is
 * purely mechanical, not a judgment call.</p>
 */
@Service
public class TaskAttemptReviewService {

    @Autowired
    private TaskAttemptRepository taskAttemptRepository;

    @Autowired
    private TaskUpdateService taskUpdateService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    // Lazy: this service is reachable from TaskAttemptReviewInbox, itself one of
    // BackpressureRegistry's queues, and both agent services depend on LLM strategies that --
    // in "local" mode -- drag in the whole Spring AI tool-calling graph, closing a cycle back
    // through this bean. Same reasoning as ClientPromptInbox's lazy OrchestratorAgentService.
    @Autowired
    @Lazy
    private EvaluatorAgentService evaluatorAgentService;

    @Autowired
    @Lazy
    private OrchestratorAgentService orchestratorAgentService;

    /**
     * Reviews one terminal attempt: evaluates it (or records a deterministic verdict for a
     * timeout/stall), then, on anything short of success, asks the orchestrator whether it's
     * worth re-attempting.
     *
     * @param attemptId the terminal attempt's id
     */
    @Transactional
    public void review(final String attemptId) {
        var attempt = this.taskAttemptRepository.findById(attemptId).orElseThrow();
        var verdictType = this.verdictFor(attempt);
        if (verdictType != TaskUpdateType.COMPLETED) {
            this.orchestratorAgentService.decideReattempt(attemptId);
        }
        this.publishSettled(attempt);
    }

    private TaskUpdateType verdictFor(final TaskAttemptEntity attempt) {
        return switch (attempt.getStatus()) {
            case SUCCEEDED, FAILED -> this.evaluatorAgentService.evaluate(attempt.getId());
            case TIMED_OUT -> this.recordDeterministicVerdict(attempt, TaskUpdateType.TIMED_OUT,
                "The attempt's deadline elapsed before it reported a result.");
            case STALLED -> this.recordDeterministicVerdict(attempt, TaskUpdateType.FAILED,
                "The attempt was reaped for repeating the same action with no progress.");
            case PENDING, DISPATCHED, RUNNING -> throw new IllegalStateException(
                "Attempt " + attempt.getId() + " is not terminal: " + attempt.getStatus());
        };
    }

    private TaskUpdateType recordDeterministicVerdict(
            final TaskAttemptEntity attempt, final TaskUpdateType type,
            final String description) {
        this.taskUpdateService.record(
            attempt.getTask(), attempt, type, description, TaskUpdateAuthor.SYSTEM);
        return type;
    }

    private void publishSettled(final TaskAttemptEntity attempt) {
        var workflowId = attempt.getTask().getTaskGraph().getTaskPlan().getWorkflow().getId();
        this.eventPublisher.publishEvent(new TaskAttemptSettledEvent(workflowId, attempt.getId()));
    }
}
