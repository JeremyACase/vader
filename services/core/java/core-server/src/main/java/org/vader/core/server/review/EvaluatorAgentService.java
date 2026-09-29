package org.vader.core.server.review;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.common.model.vader.entity.TaskUpdateAuthor;
import org.vader.common.model.vader.entity.TaskUpdateEntity;
import org.vader.common.model.vader.entity.TaskUpdateType;
import org.vader.core.server.llm.LlmRequestQueue;
import org.vader.core.server.review.model.EvaluationRequest;
import org.vader.core.server.review.model.EvaluationVerdict;
import org.vader.core.server.workflow.TaskAttemptRepository;
import org.vader.core.server.workflow.TaskUpdateRepository;
import org.vader.core.server.workflow.TaskUpdateService;

/**
 * Independently judges whether a settled attempt's own self-reported outcome
 * ({@code SUCCEEDED}/{@code FAILED}) actually holds up, rather than trusting it at face value --
 * a harness sometimes reports success for incomplete or off-target work, and occasionally the
 * reverse.
 *
 * <p>Called only from {@code TaskAttemptReviewService}, which drains the durable review pipeline
 * off {@code TaskGraphScheduler}'s own thread specifically so this evaluation's LLM call never
 * blocks workflow-progress bookkeeping.</p>
 *
 * <p>Besides pass and fail, a verdict can name the steps still left on an attempt that made real
 * but unfinished progress. When the task may be decomposed, those steps are handed to
 * {@link TaskDecompositionSaga} instead of failing the attempt outright.</p>
 */
@Service
public class EvaluatorAgentService {

    @Autowired
    private TaskAttemptRepository taskAttemptRepository;

    @Autowired
    private TaskUpdateRepository taskUpdateRepository;

    @Autowired
    private TaskUpdateService taskUpdateService;

    @Autowired
    private LlmRequestQueue requestQueue;

    @Autowired
    private ToolCallEvidenceAdapter toolCallEvidenceAdapter;

    @Autowired
    private TaskDecompositionSaga taskDecompositionSaga;

    /**
     * Evaluates one settled attempt and records the verdict as a {@link TaskUpdateEntity}.
     *
     * @param attemptId the settled attempt's id
     * @return the verdict's type: {@link TaskUpdateType#COMPLETED},
     *     {@link TaskUpdateType#FAILED}, or {@link TaskUpdateType#DECOMPOSED}
     */
    @Transactional
    public TaskUpdateType evaluate(final String attemptId) {
        var attempt = this.taskAttemptRepository.findById(attemptId).orElseThrow();
        var task = attempt.getTask();
        var request = this.requestFor(task.getTitle(), task.getDescription(), attempt);
        var verdict = this.requestQueue.submit(EvaluationLlmExecutor.class, request);
        TaskUpdateType type;
        if (this.shouldDecompose(task, verdict)) {
            this.taskDecompositionSaga.decompose(
                attempt, verdict.remainingSubtasks(), verdict.reasoning());
            type = TaskUpdateType.DECOMPOSED;
        } else {
            type = verdict.passed() ? TaskUpdateType.COMPLETED : TaskUpdateType.FAILED;
            this.taskUpdateService.record(
                task, attempt, type, verdict.reasoning(), TaskUpdateAuthor.EVALUATOR);
        }
        return type;
    }

    private boolean shouldDecompose(final TaskEntity task, final EvaluationVerdict verdict) {
        return !verdict.passed()
            && !verdict.remainingSubtasks().isEmpty()
            && this.taskDecompositionSaga.canDecompose(task);
    }

    private EvaluationRequest requestFor(
            final String taskTitle, final String taskDescription,
            final TaskAttemptEntity attempt) {
        return new EvaluationRequest(
            taskTitle, taskDescription, attempt.getStatus(), attempt.getResult(),
            attempt.getFailureReason(), this.priorUpdateDescriptions(attempt.getTask().getId()),
            this.toolCallEvidenceAdapter.lastToolCallEvidence(attempt.getId()));
    }

    private List<String> priorUpdateDescriptions(final String taskId) {
        return this.taskUpdateRepository.findByTaskIdOrderByCreatedAtAsc(taskId).stream()
            .map(update -> update.getType() + ": " + update.getDescription())
            .toList();
    }
}
