package org.vader.core.server.review;

import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.model.vader.entity.ObjectMetadataEntity;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.common.model.vader.entity.TaskUpdateAuthor;
import org.vader.common.model.vader.entity.TaskUpdateEntity;
import org.vader.common.model.vader.entity.TaskUpdateType;
import org.vader.core.server.llm.LlmRequestQueue;
import org.vader.core.server.review.model.EvaluationRequest;
import org.vader.core.server.review.model.EvaluationVerdict;
import org.vader.core.server.review.model.RemainingSubtask;
import org.vader.core.server.storage.ObjectMetadataRepository;
import org.vader.core.server.workflow.PlanOutlineBuilder;
import org.vader.core.server.workflow.TaskAttemptRepository;
import org.vader.core.server.workflow.TaskUpdateService;
import org.vader.core.server.workflow.model.PlanStep;

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
 * <p>Each attempt is judged on its own evidence alone, never shown earlier verdicts on the same
 * task: a model shown a previous rejection tends to repeat it instead of reading what this attempt
 * actually did.</p>
 *
 * <p>Besides pass and fail, a verdict can name the steps still left on an attempt that made real
 * but unfinished progress. When the task may be decomposed, those steps are handed to
 * {@link TaskDecompositionSaga} instead of failing the attempt outright -- less any step that
 * repeats a task already in the plan, since that work is already scheduled (or, for the task under
 * review itself, is a retry rather than a smaller step).</p>
 */
@Service
public class EvaluatorAgentService {

    @Autowired
    private TaskAttemptRepository taskAttemptRepository;

    @Autowired
    private TaskUpdateService taskUpdateService;

    @Autowired
    private LlmRequestQueue requestQueue;

    @Autowired
    private ToolCallEvidenceAdapter toolCallEvidenceAdapter;

    @Autowired
    private ObjectMetadataRepository objectMetadataRepository;

    @Autowired
    private TaskDecompositionSaga taskDecompositionSaga;

    @Autowired
    private PlanOutlineBuilder planOutlineBuilder;

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
        var newSteps = withoutPlannedWork(verdict.remainingSubtasks(), request.planSteps());
        TaskUpdateType type;
        if (this.shouldDecompose(task, verdict, newSteps)) {
            this.taskDecompositionSaga.decompose(attempt, newSteps, verdict.reasoning());
            type = TaskUpdateType.DECOMPOSED;
        } else {
            type = verdict.passed() ? TaskUpdateType.COMPLETED : TaskUpdateType.FAILED;
            this.taskUpdateService.record(
                task, attempt, type, verdict.reasoning(), TaskUpdateAuthor.EVALUATOR);
        }
        return type;
    }

    private boolean shouldDecompose(
            final TaskEntity task, final EvaluationVerdict verdict,
            final List<RemainingSubtask> newSteps) {
        return !verdict.passed()
            && !newSteps.isEmpty()
            && this.taskDecompositionSaga.canDecompose(task);
    }

    /** The remaining steps whose titles name no task already in the plan. */
    private static List<RemainingSubtask> withoutPlannedWork(
            final List<RemainingSubtask> steps, final List<PlanStep> planSteps) {
        var plannedTitles = planSteps.stream()
            .map(step -> normalizedTitle(step.title()))
            .toList();
        return steps.stream()
            .filter(step -> !plannedTitles.contains(normalizedTitle(step.title())))
            .toList();
    }

    /** Case, spacing and punctuation never make two titles name different work. */
    private static String normalizedTitle(final String title) {
        return title.toLowerCase(Locale.ROOT).replaceAll("[^\\p{Alnum}]+", "");
    }

    private EvaluationRequest requestFor(
            final String taskTitle, final String taskDescription,
            final TaskAttemptEntity attempt) {
        var task = attempt.getTask();
        return new EvaluationRequest(
            userRequestOf(task), planObjectiveOf(task), this.planOutlineBuilder.build(task),
            taskTitle, taskDescription, attempt.getStatus(),
            attempt.getResult(), attempt.getFailureReason(),
            this.toolCallEvidenceAdapter.lastToolCallEvidence(attempt.getId()),
            this.uploadedFilenames(attempt.getId()));
    }

    private List<String> uploadedFilenames(final String attemptId) {
        return this.objectMetadataRepository.findByTaskAttemptIdOrderByCreatedAtAsc(attemptId)
            .stream()
            .map(ObjectMetadataEntity::getOriginalFilename)
            .toList();
    }

    private static String planObjectiveOf(final TaskEntity task) {
        return task.owningTaskGraph().getTaskPlan().getObjective();
    }

    private static String userRequestOf(final TaskEntity task) {
        return task.owningTaskGraph().getTaskPlan().getWorkflow().getClientPrompt().getText();
    }
}
