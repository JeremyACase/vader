package org.vader.common.library.implementation.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.model.vader.dto.Task;
import org.vader.common.model.vader.dto.TaskGraph;
import org.vader.common.model.vader.dto.TaskPlan;
import org.vader.common.model.vader.entity.TaskEntity;

class TaskPlanDtoToEntityMapperTest {

    private final TaskPlanDtoToEntityMapper mapper = new TaskPlanDtoToEntityMapper();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
            this.mapper, "taskGraphDtoToEntityMapper", new TaskGraphDtoToEntityMapper());
    }

    private static Task task(final String id, final String title) {
        var task = new Task();
        task.setId(id);
        task.setTitle(title);
        task.setDescription(title + " description");
        return task;
    }

    @Test
    void map_withNull_returnsNull() {
        assertThat(this.mapper.map(null)).isNull();
    }

    @Test
    void map_copiesObjectiveAndWiresPlanGraphBothWays() {
        var graph = new TaskGraph();
        graph.setTasks(List.of(task("a", "Root")));

        var plan = new TaskPlan();
        plan.setObjective("ship it");
        plan.setTaskGraph(graph);

        var entity = this.mapper.map(plan);

        assertThat(entity.getObjective()).isEqualTo("ship it");
        assertThat(entity.getTaskGraph()).isNotNull();
        assertThat(entity.getTaskGraph().getTaskPlan()).isSameAs(entity);
    }

    @Test
    void map_attachesOnlyRootTasksToGraphAndNestsSubTasks() {
        var child = task("a1", "Child");
        var root = task("a", "Root");
        root.setSubTasks(List.of(child));

        var graph = new TaskGraph();
        graph.setTasks(List.of(root));
        var plan = new TaskPlan();
        plan.setObjective("o");
        plan.setTaskGraph(graph);

        var graphEntity = this.mapper.map(plan).getTaskGraph();

        assertThat(graphEntity.getTasks()).extracting(TaskEntity::getTitle).containsExactly("Root");
        var rootEntity = graphEntity.getTasks().iterator().next();
        assertThat(rootEntity.getTaskGraph()).isSameAs(graphEntity);
        assertThat(rootEntity.getSubTasks())
            .extracting(TaskEntity::getTitle).containsExactly("Child");

        var childEntity = rootEntity.getSubTasks().iterator().next();
        assertThat(childEntity.getParentTask()).isSameAs(rootEntity);
        assertThat(childEntity.getTaskGraph()).isNull();
    }

    @Test
    void map_resolvesDependsOnAgainstDtoIdsWithinTheGraph() {
        var design = task("a", "Design");
        var build = task("b", "Build");
        build.setDependsOnTaskIds(List.of("a"));

        var graph = new TaskGraph();
        graph.setTasks(List.of(design, build));
        var plan = new TaskPlan();
        plan.setObjective("o");
        plan.setTaskGraph(graph);

        var graphEntity = this.mapper.map(plan).getTaskGraph();

        var buildEntity = graphEntity.getTasks().stream()
            .filter(task -> task.getTitle().equals("Build"))
            .findFirst()
            .orElseThrow();
        assertThat(buildEntity.getDependsOn())
            .extracting(TaskEntity::getTitle)
            .containsExactly("Design");
    }

    @Test
    void map_withUnknownDependencyId_throws() {
        var build = task("b", "Build");
        build.setDependsOnTaskIds(List.of("does-not-exist"));

        var graph = new TaskGraph();
        graph.setTasks(List.of(build));
        var plan = new TaskPlan();
        plan.setObjective("o");
        plan.setTaskGraph(graph);

        assertThatThrownBy(() -> this.mapper.map(plan))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does-not-exist");
    }
}
