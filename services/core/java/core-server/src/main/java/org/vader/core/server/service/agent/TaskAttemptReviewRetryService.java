package org.vader.core.server.service.agent;

import java.time.Duration;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.model.vader.entity.OutboxMessageStatus;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskUpdateAuthor;
import org.vader.common.model.vader.entity.TaskUpdateType;
import org.vader.common.model.vader.entity.WorkflowEntity;
import org.vader.common.model.vader.entity.WorkflowStatus;
import org.vader.core.server.repository.TaskAttemptReviewOutboxMessageRepository;
import org.vader.core.server.repository.WorkflowRepository;

/**
 * Waits out an LLM outage during attempt review, honestly: the review is retried until the LLM
 * answers, and the workflow reads {@link WorkflowStatus#AWAITING_LLM} for as long as it has to
 * wait -- rather than fabricating a verdict the LLM never gave, failing the task over a transient
 * outage, or hanging silently in {@link WorkflowStatus#RUNNING}.
 *
 * <p>Owns both transitions: {@link #deferForLlmOutage} (review could not reach the LLM: put it
 * back on the queue and mark the workflow as waiting) and {@link #resumeIfNoLongerWaiting} (a
 * review succeeded: mark the workflow running again once nothing else in it is still waiting).
 * Retries indefinitely at a fixed interval, by design -- the outage is expected to end, and the
 * status tells the user exactly what the workflow is waiting on meanwhile.</p>
 */
@Service
public class TaskAttemptReviewRetryService {

    private static final Logger logger =
        LoggerFactory.getLogger(TaskAttemptReviewRetryService.class);

    @Autowired
    private TaskAttemptReviewOutboxMessageRepository messageRepository;

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private TaskUpdateService taskUpdateService;

    @Value("${vader.inbox.task-attempt-review.llm-retry-interval-ms:30000}")
    private long llmRetryIntervalMs;

    /**
     * Puts a review that failed because the LLM is unavailable back on its queue, not to be
     * claimed again until the retry interval has passed, and marks its workflow as awaiting the
     * LLM. The first time a workflow starts waiting, one task update records why; later retries
     * during the same outage stay quiet rather than adding an update every interval.
     *
     * @param messageId the review message that failed
     * @param reason why the LLM could not be reached
     */
    @Transactional
    public void deferForLlmOutage(final String messageId, final String reason) {
        var message = this.messageRepository.findById(messageId).orElseThrow();
        message.setStatus(OutboxMessageStatus.PENDING);
        message.setNextAttemptAt(
            OffsetDateTime.now().plus(Duration.ofMillis(this.llmRetryIntervalMs)));
        message.setFailureReason(reason);
        this.messageRepository.save(message);

        var attempt = message.getTaskAttempt();
        var workflow = workflowOf(attempt);
        logger.warn("Review of attempt {} deferred {}ms: the LLM is unavailable ({})",
            attempt.getId(), this.llmRetryIntervalMs, reason);
        if (workflow.getStatus() == WorkflowStatus.RUNNING) {
            this.startAwaiting(workflow, attempt, reason);
        }
    }

    /**
     * Marks a workflow running again after one of its reviews succeeds -- unless another review
     * in it is still deferred, in which case it is still genuinely waiting on the LLM.
     *
     * @param attempt the attempt whose review just succeeded
     */
    @Transactional
    public void resumeIfNoLongerWaiting(final TaskAttemptEntity attempt) {
        var workflow = workflowOf(attempt);
        var stillWaiting = this.messageRepository.existsDeferredInWorkflow(
            workflow.getId(), OutboxMessageStatus.PENDING);
        if (workflow.getStatus() == WorkflowStatus.AWAITING_LLM && !stillWaiting) {
            workflow.setStatus(WorkflowStatus.RUNNING);
            this.workflowRepository.save(workflow);
            logger.info("Workflow {} resumed: the LLM is answering again", workflow.getId());
        }
    }

    private void startAwaiting(
            final WorkflowEntity workflow, final TaskAttemptEntity attempt, final String reason) {
        workflow.setStatus(WorkflowStatus.AWAITING_LLM);
        this.workflowRepository.save(workflow);
        this.taskUpdateService.record(
            attempt.getTask(), attempt, TaskUpdateType.UPDATE,
            "Review paused: the LLM is unavailable (" + reason + "). Retrying every "
                + this.llmRetryIntervalMs / 1000 + "s until it answers.",
            TaskUpdateAuthor.SYSTEM);
    }

    private static WorkflowEntity workflowOf(final TaskAttemptEntity attempt) {
        return attempt.getTask().getTaskGraph().getTaskPlan().getWorkflow();
    }
}
