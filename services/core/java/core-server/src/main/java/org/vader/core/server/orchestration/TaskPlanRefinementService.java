package org.vader.core.server.orchestration;

import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.vader.common.model.vader.dto.ClientPrompt;
import org.vader.common.model.vader.dto.TaskPlan;
import org.vader.core.server.llm.LlmRequestQueue;
import org.vader.core.server.orchestration.model.AttachedFile;
import org.vader.core.server.orchestration.model.TaskPlanRefinementRequest;
import org.vader.core.server.orchestration.model.TaskPlanRefinementVerdict;

/**
 * Produces a task plan worth persisting: decomposes the prompt, then critiques the result --
 * structurally first (cheap, deterministic, no LLM call), and via
 * {@link TaskPlanRefinementLlmExecutor} once that passes -- and re-decomposes with the critique
 * as guidance when it finds a problem. Persists nothing.
 */
@Service
public class TaskPlanRefinementService {

    private static final Logger logger = LoggerFactory.getLogger(TaskPlanRefinementService.class);

    @Autowired
    private LlmTaskPlanAdapter taskPlanAdapter;

    @Autowired
    private TaskPlanSchemaValidator schemaValidator;

    @Autowired
    private LlmRequestQueue requestQueue;

    @Value("${vader.orchestrator.max-task-plan-revisions:1}")
    private int maxTaskPlanRevisions;

    /**
     * Decomposes {@code promptDto} and refines the plan. Dependencies the critique finds missing
     * are added to the plan directly; any other problem it finds is handled by re-decomposing
     * with the critique fed back as guidance, for up to {@link #maxTaskPlanRevisions} revisions.
     * If the plan is still flagged once that budget is spent, the last plan is used anyway
     * (logged, not thrown) rather than leaving the prompt stuck. This is not a canned fallback:
     * the plan itself was produced successfully by the LLM for this very request, it just still
     * looks questionable -- unlike an unreachable LLM, which always fails loudly rather than
     * substituting anything.
     *
     * @param promptDto the original client prompt
     * @param attachedFiles the files attached to the prompt, by name and type only
     * @return the task plan to persist
     */
    public TaskPlan refine(final ClientPrompt promptDto, final List<AttachedFile> attachedFiles) {
        var taskPlanDto = this.decompose(promptDto, attachedFiles, null);
        var revision = 0;
        var problem = this.reviewAndPatch(promptDto, taskPlanDto);
        while (Objects.nonNull(problem) && revision < this.maxTaskPlanRevisions) {
            revision++;
            taskPlanDto = this.decompose(promptDto, attachedFiles, problem);
            problem = this.reviewAndPatch(promptDto, taskPlanDto);
        }
        if (Objects.nonNull(problem)) {
            logger.warn(
                "TaskPlan refinement exhausted ({} revision(s)); proceeding with the last plan "
                    + "anyway: {}",
                this.maxTaskPlanRevisions, problem);
        }
        return taskPlanDto;
    }

    private TaskPlan decompose(
            final ClientPrompt promptDto, final List<AttachedFile> attachedFiles,
            final String revisionGuidance) {
        return this.schemaValidator.parseAndValidate(
            this.taskPlanAdapter.decompose(promptDto, attachedFiles, revisionGuidance));
    }

    /**
     * Reviews a plan and returns the problem that still needs re-planning, if any -- patching
     * {@code taskPlanDto} in place with any dependencies the critique found missing along the
     * way.
     *
     * <p>The plan's structural problem, if it has one, comes first: there is no reason to spend
     * an LLM call finding a problem a deterministic check already found for free. Otherwise the
     * refinement critique reviews it; missing dependencies it names are added directly (see
     * {@link TaskPlanDependencyPatcher} for why re-planning can't be trusted to add them), and
     * only a problem that needs the plan redone is returned.</p>
     */
    private String reviewAndPatch(final ClientPrompt promptDto, final TaskPlan taskPlanDto) {
        var structural = TaskPlanStructuralValidator.validate(taskPlanDto);
        String problem;
        if (!structural.valid()) {
            problem = structural.problem();
        } else {
            var verdict = this.requestQueue.submit(
                TaskPlanRefinementLlmExecutor.class,
                new TaskPlanRefinementRequest(promptDto.getText(), taskPlanDto));
            addMissingDependencies(taskPlanDto, verdict);
            problem = verdict.needsRevision() ? verdict.reasoning() : null;
        }
        return problem;
    }

    private static void addMissingDependencies(
            final TaskPlan taskPlanDto, final TaskPlanRefinementVerdict verdict) {
        var added = TaskPlanDependencyPatcher.apply(taskPlanDto, verdict.missingDependencies());
        if (!added.isEmpty()) {
            logger.info("Added {} dependency(ies) the plan critique found missing: {}",
                added.size(), added);
        }
    }
}
