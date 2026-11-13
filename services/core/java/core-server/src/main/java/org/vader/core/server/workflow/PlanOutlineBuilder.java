package org.vader.core.server.workflow;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.core.server.workflow.model.PlanStep;

/**
 * Builds the outline of the plan a task belongs to: every task in its graph, each followed by
 * the subtasks spawned under it, siblings in the order their dependencies let them run. The
 * agent running a task needs it to do only its own share of the user's request, and the
 * evaluator needs it to judge the task on that share alone -- neither should take on work
 * another task already owns. Subtasks a superseded attempt spawned are left out: they are no
 * longer part of the plan.
 */
@Component
public class PlanOutlineBuilder {

    @Autowired
    private LiveSubtaskFilter liveSubtaskFilter;

    /**
     * Outlines the plan that a task belongs to.
     *
     * @param underReview the task being run or evaluated; marked in the outline
     * @return every task in the plan, depth first
     */
    public List<PlanStep> build(final TaskEntity underReview) {
        return this.outline(underReview.owningTaskGraph().getTasks(), 0, underReview).toList();
    }

    private Stream<PlanStep> outline(
            final Collection<TaskEntity> siblings, final int depth, final TaskEntity underReview) {
        return siblings.stream()
            .sorted(Comparator.comparingInt(PlanOutlineBuilder::dependencyDepth))
            .flatMap(task -> Stream.concat(
                Stream.of(new PlanStep(task.getTitle(), task.getDescription(), depth,
                    task.equals(underReview))),
                this.outline(this.liveSubtaskFilter.liveSubtasksOf(task), depth + 1,
                    underReview)));
    }

    /**
     * The longest chain of dependencies leading to a task, so a task sorts after everything it
     * waits on. Plans are validated acyclic before they are persisted.
     */
    private static int dependencyDepth(final TaskEntity task) {
        return task.getDependsOn().stream()
            .mapToInt(dependency -> dependencyDepth(dependency) + 1)
            .max()
            .orElse(0);
    }
}
