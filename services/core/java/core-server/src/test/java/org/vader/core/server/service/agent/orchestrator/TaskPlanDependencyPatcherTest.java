package org.vader.core.server.service.agent.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.vader.common.model.vader.dto.Task;
import org.vader.common.model.vader.dto.TaskGraph;
import org.vader.common.model.vader.dto.TaskPlan;
import org.vader.core.server.models.TaskPlanRefinementVerdict.MissingDependency;

class TaskPlanDependencyPatcherTest {

    private static Task task(final String title) {
        var task = new Task();
        task.setId(UUID.randomUUID().toString());
        task.setTitle(title);
        task.setDependsOnTaskIds(List.of());
        return task;
    }

    private static TaskPlan planOf(final Task... tasks) {
        var taskGraph = new TaskGraph();
        taskGraph.setTasks(new ArrayList<>(List.of(tasks)));
        var taskPlan = new TaskPlan();
        taskPlan.setTaskGraph(taskGraph);
        return taskPlan;
    }

    private static MissingDependency missing(final String task, final String... dependsOn) {
        return new MissingDependency(task, List.of(dependsOn));
    }

    @Test
    void apply_addsTheDependencyTheCriticNamed() {
        var read = task("Read Spreadsheet");
        var identify = task("Identify Key Data");

        var added = TaskPlanDependencyPatcher.apply(
            planOf(read, identify), List.of(missing("Identify Key Data", "Read Spreadsheet")));

        assertThat(identify.getDependsOnTaskIds()).containsExactly(read.getId());
        assertThat(added).containsExactly(
            "'Identify Key Data' now depends on 'Read Spreadsheet'");
    }

    @Test
    void apply_resolvesTitlesTheWayTheModelRepeatsThem() {
        var read = task("Read Spreadsheet");
        var identify = task("Identify Key Data");

        TaskPlanDependencyPatcher.apply(
            planOf(read, identify), List.of(missing("identify key data", "\"read spreadsheet\"")));

        assertThat(identify.getDependsOnTaskIds()).containsExactly(read.getId());
    }

    @Test
    void apply_addsChainOfDependenciesInOnePass() {
        var read = task("Read Spreadsheet");
        var identify = task("Identify Key Data");
        var report = task("Generate Report");

        TaskPlanDependencyPatcher.apply(planOf(read, identify, report), List.of(
            missing("Identify Key Data", "Read Spreadsheet"),
            missing("Generate Report", "Identify Key Data")));

        assertThat(identify.getDependsOnTaskIds()).containsExactly(read.getId());
        assertThat(report.getDependsOnTaskIds()).containsExactly(identify.getId());
    }

    @Test
    void apply_skipsEdgeThatWouldCloseCycle() {
        var read = task("Read Spreadsheet");
        var identify = task("Identify Key Data");
        identify.setDependsOnTaskIds(List.of(read.getId()));

        var added = TaskPlanDependencyPatcher.apply(
            planOf(read, identify), List.of(missing("Read Spreadsheet", "Identify Key Data")));

        assertThat(added).isEmpty();
        assertThat(read.getDependsOnTaskIds()).isEmpty();
        assertThat(TaskPlanStructuralValidator.validate(planOf(read, identify)).valid()).isTrue();
    }

    @Test
    void apply_skipsTransitiveCycleToo() {
        var read = task("Read Spreadsheet");
        var identify = task("Identify Key Data");
        var report = task("Generate Report");
        identify.setDependsOnTaskIds(List.of(read.getId()));
        report.setDependsOnTaskIds(List.of(identify.getId()));

        var added = TaskPlanDependencyPatcher.apply(
            planOf(read, identify, report),
            List.of(missing("Read Spreadsheet", "Generate Report")));

        assertThat(added).isEmpty();
        assertThat(read.getDependsOnTaskIds()).isEmpty();
    }

    @Test
    void apply_skipsUnknownTitlesSelfDependenciesAndExistingEdges() {
        var read = task("Read Spreadsheet");
        var identify = task("Identify Key Data");
        identify.setDependsOnTaskIds(List.of(read.getId()));

        var added = TaskPlanDependencyPatcher.apply(planOf(read, identify), List.of(
            missing("Identify Key Data", "Read Spreadsheet"),
            missing("Identify Key Data", "Identify Key Data"),
            missing("Summarize Findings", "Read Spreadsheet"),
            missing("Identify Key Data", "Open The Spreadsheet")));

        assertThat(added).isEmpty();
        assertThat(identify.getDependsOnTaskIds()).containsExactly(read.getId());
    }

    @Test
    void apply_toleratesMissingDependencyWithNoDependsOnList() {
        var read = task("Read Spreadsheet");

        var added = TaskPlanDependencyPatcher.apply(
            planOf(read), List.of(new MissingDependency("Read Spreadsheet", null)));

        assertThat(added).isEmpty();
    }
}
