package org.vader.core.server.workflow;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskAttemptStatus;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.common.model.vader.entity.WorkflowEntity;
import org.vader.core.server.llm.LlmRequestQueue;
import org.vader.core.server.workflow.model.DeliveredFile;
import org.vader.core.server.workflow.model.TaskOutcome;
import org.vader.core.server.workflow.model.WorkflowSynthesisRequest;

/**
 * Writes a completed workflow's final answer: one response synthesized from every task's own
 * result, rather than a list of what each task did -- the same relationship a project's final
 * report has to its individual work items.
 *
 * <p>Called by {@link TaskGraphScheduler} exactly once, right as a workflow is marked terminal.
 * Synthesis is best-effort: a failure here (e.g. the local LLM is unreachable) falls back to a
 * simple derived summary rather than leaving the workflow's completion blocked on it -- closing
 * out the workflow is the part that must not fail.</p>
 *
 * <p>Every file the tasks uploaded is listed after the answer with its download path. The list is
 * appended here rather than left to the model, so a link is never misquoted or dropped.</p>
 */
@Service
public class WorkflowSynthesisService {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowSynthesisService.class);

    @Autowired
    private TaskAttemptRepository taskAttemptRepository;

    @Autowired
    private DeliveredFileListBuilder deliveredFileListBuilder;

    @Autowired
    private LlmRequestQueue requestQueue;

    /**
     * Synthesizes a completed workflow's final answer.
     *
     * @param workflow the workflow, with every task already in a terminal state
     * @return the synthesized answer, or a simple derived summary if synthesis itself failed,
     *     followed by the list of delivered files, if any
     */
    public String synthesize(final WorkflowEntity workflow) {
        var tasks = workflow.getTaskPlan().getTaskGraph().getTasks();
        var outcomes = tasks.stream()
            .map(this::outcomeFor)
            .toList();
        var files = this.deliveredFiles(workflow, tasks);
        var request = new WorkflowSynthesisRequest(
            workflow.getClientPrompt().getText(),
            workflow.getTaskPlan().getObjective(),
            outcomes,
            files);

        String result;
        try {
            result = this.requestQueue.submit(WorkflowSynthesisLlmExecutor.class, request);
        } catch (RuntimeException e) {
            logger.warn("Workflow synthesis failed for workflow {}: {}",
                workflow.getId(), e.getMessage());
            result = this.fallbackSummary(outcomes);
        }
        return result + filesListing(files);
    }

    /** Best-effort like synthesis itself: a storage failure loses the list, not the workflow. */
    private List<DeliveredFile> deliveredFiles(
        final WorkflowEntity workflow, final Collection<TaskEntity> tasks) {
        List<DeliveredFile> files;
        try {
            files = this.deliveredFileListBuilder.build(tasks);
        } catch (RuntimeException e) {
            logger.warn("Could not list delivered files for workflow {}: {}",
                workflow.getId(), e.getMessage());
            files = List.of();
        }
        return files;
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
            ? Objects.requireNonNullElse(attempt.effectiveResult(), "(no result reported)")
            : Objects.requireNonNullElse(
                attempt.getFailureReason(), "(no failure reason reported)");
        return new TaskOutcome(task.getTitle(), succeeded, output);
    }

    private String fallbackSummary(final List<TaskOutcome> outcomes) {
        var succeededCount = outcomes.stream().filter(TaskOutcome::succeeded).count();
        return "Completed " + succeededCount + " of " + outcomes.size() + " tasks.";
    }

    private static String filesListing(final List<DeliveredFile> files) {
        var lines = files.stream()
            .map(file -> "- " + file.filename() + ": " + file.downloadPath())
            .collect(Collectors.joining("\n"));
        return files.isEmpty() ? "" : "\n\nFiles:\n" + lines;
    }
}
