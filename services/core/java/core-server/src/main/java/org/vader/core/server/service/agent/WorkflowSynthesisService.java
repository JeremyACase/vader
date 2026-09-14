package org.vader.core.server.service.agent;

import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskAttemptStatus;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.common.model.vader.entity.WorkflowEntity;
import org.vader.core.server.models.TaskOutcome;
import org.vader.core.server.models.WorkflowSynthesisRequest;
import org.vader.core.server.repository.TaskAttemptRepository;
import org.vader.core.server.service.strategies.synthesis.interfaces.InterfaceWorkflowSynthesisStrategy;

/**
 * Writes a completed workflow's final answer: one response synthesized from every task's own
 * result, rather than a list of what each task did -- the same relationship a project's final
 * report has to its individual work items.
 *
 * <p>Called by {@link TaskGraphScheduler} exactly once, right as a workflow is marked terminal.
 * Synthesis is best-effort: a failure here (e.g. the local LLM is unreachable) falls back to a
 * simple derived summary rather than leaving the workflow's completion blocked on it -- closing
 * out the workflow is the part that must not fail.</p>
 */
@Service
public class WorkflowSynthesisService {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowSynthesisService.class);

    @Autowired
    private TaskAttemptRepository taskAttemptRepository;

    @Autowired
    private InterfaceWorkflowSynthesisStrategy synthesisStrategy;

    /**
     * Synthesizes a completed workflow's final answer.
     *
     * @param workflow the workflow, with every task already in a terminal state
     * @return the synthesized answer, or a simple derived summary if synthesis itself failed
     */
    public String synthesize(final WorkflowEntity workflow) {
        var outcomes = workflow.getTaskPlan().getTaskGraph().getTasks().stream()
            .map(this::outcomeFor)
            .toList();
        var request = new WorkflowSynthesisRequest(
            workflow.getClientPrompt().getText(),
            workflow.getTaskPlan().getObjective(),
            outcomes);

        String result;
        try {
            result = this.synthesisStrategy.synthesize(request);
        } catch (RuntimeException e) {
            logger.warn("Workflow synthesis failed for workflow {}: {}",
                workflow.getId(), e.getMessage());
            result = this.fallbackSummary(outcomes);
        }
        return result;
    }

    private TaskOutcome outcomeFor(final TaskEntity task) {
        var latest = this.taskAttemptRepository.findFirstByTaskIdOrderByAttemptNumberDesc(
            task.getId());
        return latest.map(attempt -> this.outcomeFrom(task, attempt))
            .orElseGet(() -> new TaskOutcome(task.getTitle(), false, "Never attempted."));
    }

    private TaskOutcome outcomeFrom(final TaskEntity task, final TaskAttemptEntity attempt) {
        var succeeded = attempt.getStatus() == TaskAttemptStatus.SUCCEEDED;
        var output = succeeded
            ? Objects.requireNonNullElse(attempt.getResult(), "(no result reported)")
            : Objects.requireNonNullElse(
                attempt.getFailureReason(), "(no failure reason reported)");
        return new TaskOutcome(task.getTitle(), succeeded, output);
    }

    private String fallbackSummary(final List<TaskOutcome> outcomes) {
        var succeededCount = outcomes.stream().filter(TaskOutcome::succeeded).count();
        return "Completed " + succeededCount + " of " + outcomes.size() + " tasks.";
    }
}
