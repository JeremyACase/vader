package org.vader.core.server.review;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskUpdateAuthor;
import org.vader.common.model.vader.entity.TaskUpdateType;
import org.vader.common.model.vader.entity.WorkflowEntity;
import org.vader.common.model.vader.entity.WorkflowStatus;
import org.vader.core.server.events.EventPublishingFacade;
import org.vader.core.server.workflow.TaskAttemptRepository;
import org.vader.core.server.workflow.TaskAttemptSettledEvent;
import org.vader.core.server.workflow.TaskUpdateService;

/**
 * Settles an attempt whose review failed for any reason but an LLM outage -- typically a reply
 * that could not be parsed -- so its workflow still reaches an outcome instead of waiting forever
 * on a verdict nothing will record.
 *
 * <p>Nothing verified the attempt, so it counts as failed, and its task is re-attempted while
 * attempts remain without asking the LLM whether to: the reattempt decision's own reply may be
 * what failed. A reattempt is shown this verdict as the reason the attempt was rejected, so it
 * says only that the attempt went unreviewed, never that its work was wrong.</p>
 */
@Service
public class TaskAttemptReviewFallbackService {

    static final String UNREVIEWED_VERDICT = "This attempt could not be reviewed: the reviewer's "
        + "reply was unusable, so the attempt counts as failed without any judgment of its work.";

    @Autowired
    private TaskAttemptRepository taskAttemptRepository;

    @Autowired
    private TaskUpdateService taskUpdateService;

    @Autowired
    private EventPublishingFacade eventPublishingFacade;

    @Autowired
    private TaskAttemptReviewRetryService retryService;

    // Lazy for the same cycle as in TaskAttemptReviewService: it reaches the Spring AI
    // tool-calling graph, which leads back to this inbox's BackpressureRegistry.
    @Autowired
    @Lazy
    private ReattemptDecisionService reattemptDecisionService;

    /**
     * Records an unreviewed attempt as failed, re-attempts its task if attempts remain, and lets
     * the workflow move on.
     *
     * @param attemptId the attempt whose review failed
     */
    @Transactional
    public void settleUnreviewed(final String attemptId) {
        var attempt = this.taskAttemptRepository.findById(attemptId).orElseThrow();
        // A cancelled workflow is waiting on nothing, as in TaskAttemptReviewService.
        if (workflowOf(attempt).getStatus() == WorkflowStatus.CANCELLED) {
            return;
        }
        this.taskUpdateService.record(attempt.getTask(), attempt, TaskUpdateType.FAILED,
            UNREVIEWED_VERDICT, TaskUpdateAuthor.SYSTEM);
        this.reattemptDecisionService.reattemptIfAttemptsRemain(attemptId);
        // The LLM did answer, if unusably, so the workflow is no longer waiting on it.
        this.retryService.resumeIfNoLongerWaiting(attempt);
        this.eventPublishingFacade.publish(
            new TaskAttemptSettledEvent(workflowOf(attempt).getId(), attempt.getId()));
    }

    private static WorkflowEntity workflowOf(final TaskAttemptEntity attempt) {
        return attempt.getTask().owningTaskGraph().getTaskPlan().getWorkflow();
    }
}
