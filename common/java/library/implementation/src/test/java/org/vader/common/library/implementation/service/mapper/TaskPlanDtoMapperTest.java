package org.vader.common.library.implementation.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.common.model.vader.entity.TaskGraphEntity;
import org.vader.common.model.vader.entity.TaskPlanEntity;

class TaskPlanDtoMapperTest {

    private final TaskPlanDtoMapper mapper = new TaskPlanDtoMapper();

    @BeforeEach
    void setUp() {
        var taskGraphDtoMapper = new TaskGraphDtoMapper();
        ReflectionTestUtils.setField(taskGraphDtoMapper, "taskDtoMapper", new TaskDtoMapper());
        ReflectionTestUtils.setField(this.mapper, "taskGraphDtoMapper", taskGraphDtoMapper);
    }

    @Test
    void map_withNull_returnsNull() {
        assertThat(this.mapper.map((TaskPlanEntity) null)).isNull();
    }

    @Test
    void map_copiesObjectiveAndNestedGraph() {
        var task = new TaskEntity();
        task.setId("t1");
        task.setTitle("t1");
        task.setDescription("t1");

        var graph = new TaskGraphEntity();
        graph.setId("g1");
        graph.setTasks(new LinkedHashSet<>(List.of(task)));

        var entity = new TaskPlanEntity();
        entity.setId("p1");
        entity.setObjective("ship the feature");
        entity.setTaskGraph(graph);

        var dto = this.mapper.map(entity);

        assertThat(dto.getId()).isEqualTo("p1");
        assertThat(dto.getObjective()).isEqualTo("ship the feature");
        assertThat(dto.getTaskGraph().getId()).isEqualTo("g1");
        assertThat(dto.getTaskGraph().getTasks()).extracting("id").containsExactly("t1");
        assertThat(dto.getModelType()).isEqualTo("TaskPlan");
    }

    @Test
    void map_withNoGraph_leavesTaskGraphNull() {
        var entity = new TaskPlanEntity();
        entity.setObjective("objective only");

        assertThat(this.mapper.map(entity).getTaskGraph()).isNull();
    }
}
