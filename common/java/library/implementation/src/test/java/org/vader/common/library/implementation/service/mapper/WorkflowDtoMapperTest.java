package org.vader.common.library.implementation.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.model.vader.entity.ClientPromptEntity;
import org.vader.common.model.vader.entity.TaskPlanEntity;
import org.vader.common.model.vader.entity.WorkflowEntity;

class WorkflowDtoMapperTest {

    private final WorkflowDtoMapper mapper = new WorkflowDtoMapper();

    @BeforeEach
    void setUp() {
        var taskGraphDtoMapper = new TaskGraphDtoMapper();
        ReflectionTestUtils.setField(taskGraphDtoMapper, "taskDtoMapper", new TaskDtoMapper());
        var taskPlanDtoMapper = new TaskPlanDtoMapper();
        ReflectionTestUtils.setField(taskPlanDtoMapper, "taskGraphDtoMapper", taskGraphDtoMapper);
        ReflectionTestUtils.setField(this.mapper, "taskPlanDtoMapper", taskPlanDtoMapper);
    }

    @Test
    void map_withNull_returnsNull() {
        assertThat(this.mapper.map((WorkflowEntity) null)).isNull();
    }

    @Test
    void map_emitsClientPromptAsShallowIdReferenceAndCopiesTaskPlan() {
        var clientPrompt = new ClientPromptEntity();
        clientPrompt.setId("cp1");

        var taskPlan = new TaskPlanEntity();
        taskPlan.setId("p1");
        taskPlan.setObjective("do the thing");

        var entity = new WorkflowEntity();
        entity.setId("w1");
        entity.setClientPrompt(clientPrompt);
        entity.setTaskPlan(taskPlan);

        var dto = this.mapper.map(entity);

        assertThat(dto.getId()).isEqualTo("w1");
        assertThat(dto.getClientPromptId()).isEqualTo("cp1");
        assertThat(dto.getTaskPlan().getId()).isEqualTo("p1");
        assertThat(dto.getTaskPlan().getObjective()).isEqualTo("do the thing");
        assertThat(dto.getModelType()).isEqualTo("Workflow");
    }

    @Test
    void map_withNoClientPrompt_leavesClientPromptIdNull() {
        var entity = new WorkflowEntity();

        assertThat(this.mapper.map(entity).getClientPromptId()).isNull();
    }

    @Test
    void map_withNoTaskPlan_leavesTaskPlanNull() {
        var entity = new WorkflowEntity();

        assertThat(this.mapper.map(entity).getTaskPlan()).isNull();
    }
}
