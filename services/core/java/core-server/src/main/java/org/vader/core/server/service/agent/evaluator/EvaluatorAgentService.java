package org.vader.core.server.service.agent.evaluator;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskUpdateAuthor;
import org.vader.common.model.vader.entity.TaskUpdateEntity;
import org.vader.common.model.vader.entity.TaskUpdateType;
import org.vader.core.server.models.EvaluationRequest;
import org.vader.core.server.repository.TaskAttemptRepository;
import org.vader.core.server.repository.TaskUpdateRepository;
import org.vader.core.server.service.agent.TaskUpdateService;
import org.vader.core.server.service.agent.evaluator.strategies.interfaces.InterfaceEvaluatorStrategy;

/**
 * Independently judges whether a settled attempt's own self-reported outcome
 * ({@code SUCCEEDED}/{@code FAILED}) actually holds up, rather than trusting it at face value --
 * a harness sometimes reports success for incomplete or off-target work, and occasionally the
 * reverse.
 *
 * <p>Called only from {@code TaskAttemptReviewService}, which drains the durable review pipeline
 * off {@code TaskGraphScheduler}'s own thread specifically so this evaluation's LLM call never
 * blocks workflow-progress bookkeeping.</p>
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
    private InterfaceEvaluatorStrategy evaluatorStrategy;

    /**
     * Evaluates one settled attempt and records the verdict as a {@link TaskUpdateEntity}.
     *
     * @param attemptId the settled attempt's id
     * @return the verdict's type, {@link TaskUpdateType#COMPLETED} or
     *     {@link TaskUpdateType#FAILED}
     */
    @Transactional
    public TaskUpdateType evaluate(final String attemptId) {
        var attempt = this.taskAttemptRepository.findById(attemptId).orElseThrow();
        var task = attempt.getTask();
        var request = this.requestFor(task.getTitle(), task.getDescription(), attempt);
        var verdict = this.evaluatorStrategy.evaluate(request);
        var type = verdict.passed() ? TaskUpdateType.COMPLETED : TaskUpdateType.FAILED;
        this.taskUpdateService.record(
            task, attempt, type, verdict.reasoning(), TaskUpdateAuthor.EVALUATOR);
        return type;
    }

    private EvaluationRequest requestFor(
            final String taskTitle, final String taskDescription,
            final TaskAttemptEntity attempt) {
        return new EvaluationRequest(
            taskTitle, taskDescription, attempt.getStatus(), attempt.getResult(),
            attempt.getFailureReason(), this.priorUpdateDescriptions(attempt.getTask().getId()));
    }

    private List<String> priorUpdateDescriptions(final String taskId) {
        return this.taskUpdateRepository.findByTaskIdOrderByCreatedAtAsc(taskId).stream()
            .map(update -> update.getType() + ": " + update.getDescription())
            .toList();
    }
}
