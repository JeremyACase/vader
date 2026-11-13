package org.vader.core.server.orchestration;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vader.core.server.orchestration.model.AttachedFile;
import org.vader.core.server.orchestration.model.LlmTaskPlan;

/**
 * Folds the plan for a single-file transform -- one attached file in, one deliverable file out --
 * into a single task, whatever the planner split it into.
 *
 * <p>Such a job is one script's worth of work, and every task runs in a fresh sandbox, so
 * splitting it only adds hand-offs: a "read the file" step leaves nothing behind, and a failure
 * in any intermediate step fails the whole workflow. Small planners split these jobs even when
 * told not to, so the rule is applied here rather than left to the prompt. The merged task keeps
 * every step the planner wrote, in order, so nothing it specified is lost.</p>
 */
public final class SingleFileTransformPlanCollapser {

    private static final Logger logger =
        LoggerFactory.getLogger(SingleFileTransformPlanCollapser.class);

    private SingleFileTransformPlanCollapser() {
    }

    /**
     * Returns the plan unchanged unless it is a multi-task plan for a single-file transform, in
     * which case it returns the same plan with its tasks merged into one.
     *
     * @param plan the planner's plan, already checked to have at least one task
     * @param attachedFiles the files attached to the request
     * @return the plan to adapt
     */
    public static LlmTaskPlan collapse(
            final LlmTaskPlan plan, final List<AttachedFile> attachedFiles) {
        return isSingleFileTransform(plan, attachedFiles) && plan.tasks().size() > 1
            ? collapsed(plan)
            : plan;
    }

    private static LlmTaskPlan collapsed(final LlmTaskPlan plan) {
        logger.info("Collapsing a {}-task plan for single-file transform {} into one task",
            plan.tasks().size(), plan.deliverables());
        return new LlmTaskPlan(
            plan.reasoning(), plan.objective(), plan.deliverables(),
            List.of(merged(plan.tasks())));
    }

    private static boolean isSingleFileTransform(
            final LlmTaskPlan plan, final List<AttachedFile> attachedFiles) {
        return attachedFiles.size() == 1 && distinctDeliverableCount(plan.deliverables()) == 1;
    }

    private static long distinctDeliverableCount(final List<String> deliverables) {
        return Objects.requireNonNullElse(deliverables, List.<String>of()).stream()
            .filter(Objects::nonNull)
            .map(name -> name.strip().toLowerCase(Locale.ROOT))
            .filter(name -> !name.isEmpty())
            .distinct()
            .count();
    }

    /**
     * One task doing every step in order, titled after the last step -- the one that writes the
     * deliverable.
     */
    private static LlmTaskPlan.LlmTask merged(final List<LlmTaskPlan.LlmTask> tasks) {
        var steps = IntStream.range(0, tasks.size())
            .mapToObj(index -> (index + 1) + ". " + tasks.get(index).description())
            .collect(Collectors.joining("\n"));
        return new LlmTaskPlan.LlmTask(tasks.get(tasks.size() - 1).title(), steps, List.of());
    }
}
