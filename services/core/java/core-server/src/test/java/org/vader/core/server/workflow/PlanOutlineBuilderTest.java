package org.vader.core.server.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.common.model.vader.entity.TaskGraphEntity;
import org.vader.core.server.workflow.model.PlanStep;

class PlanOutlineBuilderTest {

    private final PlanOutlineBuilder builder = new PlanOutlineBuilder();

    private static TaskEntity task(final String title, final TaskEntity... dependsOn) {
        var task = new TaskEntity();
        task.setTitle(title);
        task.setDescription(title + " description");
        task.getDependsOn().addAll(List.of(dependsOn));
        return task;
    }

    private static void addRoots(final TaskGraphEntity graph, final TaskEntity... roots) {
        List.of(roots).forEach(root -> {
            root.setTaskGraph(graph);
            graph.getTasks().add(root);
        });
    }

    private static void addSubtasks(final TaskEntity parent, final TaskEntity... subtasks) {
        List.of(subtasks).forEach(subtask -> {
            subtask.setParentTask(parent);
            parent.getSubTasks().add(subtask);
        });
    }

    private static PlanStep step(final String title, final int depth, final boolean underReview) {
        return new PlanStep(title, title + " description", depth, underReview);
    }

    @Test
    void build_ordersSiblingsByDependencyAndNestsSubtasksUnderTheirParent() {
        var document = task("Document");
        var analyze = task("Analyze", document);
        var assess = task("Assess", analyze);
        var inspect = task("Inspect");
        var summarize = task("Summarize", inspect);
        addSubtasks(document, summarize, inspect);
        addRoots(new TaskGraphEntity(), assess, analyze, document);

        var outline = this.builder.build(summarize);

        assertThat(outline).containsExactly(
            step("Document", 0, false),
            step("Inspect", 1, false),
            step("Summarize", 1, true),
            step("Analyze", 0, false),
            step("Assess", 0, false));
    }

    @Test
    void build_marksThePlannerTaskUnderReview() {
        var only = task("Only");
        addRoots(new TaskGraphEntity(), only);

        assertThat(this.builder.build(only)).containsExactly(step("Only", 0, true));
    }
}
