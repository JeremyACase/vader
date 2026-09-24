package org.vader.core.server.service.agent.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.vader.common.model.vader.dto.Task;
import org.vader.common.model.vader.dto.TaskGraph;
import org.vader.common.model.vader.dto.TaskPlan;

class TaskPlanStructuralValidatorTest {

    private static Task task(final String id, final List<String> dependsOnTaskIds) {
        var task = new Task();
        task.setId(id);
        task.setTitle("task-" + id);
        task.setDescription("description");
        task.setDependsOnTaskIds(dependsOnTaskIds);
        return task;
    }

    private static TaskPlan planOf(final Task... tasks) {
        var taskGraph = new TaskGraph();
        taskGraph.setTasks(List.of(tasks));
        var taskPlan = new TaskPlan();
        taskPlan.setObjective("ship it");
        taskPlan.setTaskGraph(taskGraph);
        return taskPlan;
    }

    @Test
    void validate_approvesPlanWithNoDependencies() {
        var result = TaskPlanStructuralValidator.validate(
            planOf(task("a", List.of()), task("b", List.of())));

        assertThat(result.valid()).isTrue();
        assertThat(result.problem()).isNull();
    }

    @Test
    void validate_approvesValidDependencyChain() {
        var result = TaskPlanStructuralValidator.validate(
            planOf(task("a", List.of()), task("b", List.of("a"))));

        assertThat(result.valid()).isTrue();
    }

    @Test
    void validate_rejectsAnEmptyTaskList() {
        var taskGraph = new TaskGraph();
        taskGraph.setTasks(List.of());
        var taskPlan = new TaskPlan();
        taskPlan.setObjective("ship it");
        taskPlan.setTaskGraph(taskGraph);

        var result = TaskPlanStructuralValidator.validate(taskPlan);

        assertThat(result.valid()).isFalse();
        assertThat(result.problem()).contains("no tasks");
    }

    @Test
    void validate_rejectsDanglingDependency() {
        var result = TaskPlanStructuralValidator.validate(
            planOf(task("a", List.of("does-not-exist"))));

        assertThat(result.valid()).isFalse();
        assertThat(result.problem()).contains("unknown task id 'does-not-exist'");
    }

    @Test
    void validate_rejectsTwoNodeDependencyCycle() {
        var result = TaskPlanStructuralValidator.validate(
            planOf(task("a", List.of("b")), task("b", List.of("a"))));

        assertThat(result.valid()).isFalse();
        assertThat(result.problem()).contains("dependency cycle");
    }

    @Test
    void validate_rejectsSelfDependency() {
        var result = TaskPlanStructuralValidator.validate(planOf(task("a", List.of("a"))));

        assertThat(result.valid()).isFalse();
        assertThat(result.problem()).contains("dependency cycle");
    }
}
