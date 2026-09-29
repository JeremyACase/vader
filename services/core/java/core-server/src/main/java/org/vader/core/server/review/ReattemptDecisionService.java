package org.vader.core.server.review;

import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskUpdateAuthor;
import org.vader.common.model.vader.entity.TaskUpdateEntity;
import org.vader.common.model.vader.entity.TaskUpdateType;
import org.vader.core.server.llm.LlmRequestQueue;
import org.vader.core.server.review.model.ReattemptDecisionRequest;
import org.vader.core.server.workflow.TaskAttemptRepository;
import org.vader.core.server.workflow.TaskGraphScheduler;
import org.vader.core.server.workflow.TaskUpdateRepository;
import org.vader.core.server.workflow.TaskUpdateService;

/**
 * Decides whether a failed attempt is worth re-attempting, and dispatches a fresh attempt if so.
 * Called only from {@code TaskAttemptReviewService}, after an evaluator (or a deterministic
 * timeout/stall verdict) has already judged the attempt a failure.
 */
@Service
public class ReattemptDecisionService {

    @Autowired
    private TaskAttemptRepository taskAttemptRepository;

    @Autowired
    private TaskUpdateRepository taskUpdateRepository;

    @Autowired
    private TaskUpdateService taskUpdateService;

    @Autowired
    private TaskGraphScheduler taskGraphScheduler;

    @Autowired
    private LlmRequestQueue requestQueue;

    @Value("${vader.agent-harness.max-attempts-per-task:3}")
    private int maxAttemptsPerTask;

    /**
     * Decides whether to re-attempt a failed attempt's task.
     *
     * <p>The attempt-count cap is a hard ceiling checked here before any LLM call: once reached,
     * this deterministically gives up rather than asking the LLM at all.</p>
     *
     * @param attemptId the failed attempt's id
     */
    @Transactional
    public void decideReattempt(final String attemptId) {
        var attempt = this.taskAttemptRepository.findById(attemptId).orElseThrow();
        var task = attempt.getTask();

        if (attempt.getAttemptNumber() >= this.maxAttemptsPerTask) {
            this.taskUpdateService.record(task, attempt, TaskUpdateType.UPDATE,
                "Attempt cap (" + this.maxAttemptsPerTask + ") reached; not re-attempting.",
                TaskUpdateAuthor.ORCHESTRATOR);
            return;
        }

        var latestFailureReasoning = this.latestFailureReasoning(attempt);
        var request = new ReattemptDecisionRequest(
            task.getTitle(), task.getDescription(), attempt.getAttemptNumber(),
            this.maxAttemptsPerTask, latestFailureReasoning,
            this.priorAttemptUpdateDescriptions(task.getId(), attempt.getId()));

        var decision = this.requestQueue.submit(ReattemptDecisionLlmExecutor.class, request);
        this.taskUpdateService.record(task, attempt, TaskUpdateType.UPDATE, decision.reasoning(),
            TaskUpdateAuthor.ORCHESTRATOR);
        if (decision.shouldReattempt()) {
            this.taskGraphScheduler.dispatch(task, attempt.getAttemptNumber() + 1);
        }
    }

    private String latestFailureReasoning(final TaskAttemptEntity attempt) {
        var verdictTypes = List.of(TaskUpdateType.FAILED, TaskUpdateType.TIMED_OUT);
        return this.taskUpdateRepository
            .findFirstByTaskAttemptIdAndTypeInOrderByCreatedAtDesc(attempt.getId(), verdictTypes)
            .map(TaskUpdateEntity::getDescription)
            .orElse("(no failure reasoning recorded)");
    }

    /**
     * Describes only the updates recorded against <em>earlier</em> attempts of this task. The
     * attempt being judged is excluded -- its failure verdict already goes in as
     * {@code latestFailureReasoning}, and repeating it reads to a model as a failure that keeps
     * recurring -- and so are task-level updates tied to no attempt at all.
     */
    private List<String> priorAttemptUpdateDescriptions(
            final String taskId, final String currentAttemptId) {
        return this.taskUpdateRepository.findByTaskIdOrderByCreatedAtAsc(taskId).stream()
            .filter(update -> isFromAnEarlierAttempt(update, currentAttemptId))
            .map(update -> "Attempt " + update.getTaskAttempt().getAttemptNumber() + " "
                + update.getType() + ": " + update.getDescription())
            .toList();
    }

    private static boolean isFromAnEarlierAttempt(
            final TaskUpdateEntity update, final String currentAttemptId) {
        return Objects.nonNull(update.getTaskAttempt())
            && !currentAttemptId.equals(update.getTaskAttempt().getId());
    }
}
