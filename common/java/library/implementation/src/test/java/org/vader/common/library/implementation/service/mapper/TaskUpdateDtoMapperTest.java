package org.vader.common.library.implementation.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.common.model.vader.entity.TaskUpdateEntity;
import org.vader.common.model.vader.entity.TaskUpdateType;

class TaskUpdateDtoMapperTest {

    private final TaskUpdateDtoMapper mapper = new TaskUpdateDtoMapper();

    private static TaskUpdateEntity taskUpdate(final String id, final TaskUpdateType type) {
        var entity = new TaskUpdateEntity();
        entity.setId(id);
        entity.setType(type);
        entity.setDescription(type + " description");
        return entity;
    }

    @Test
    void map_withNull_returnsNull() {
        assertThat(this.mapper.map((TaskUpdateEntity) null)).isNull();
    }

    @Test
    void map_copiesScalarFields() {
        var dto = this.mapper.map(taskUpdate("u1", TaskUpdateType.FAILED));

        assertThat(dto.getId()).isEqualTo("u1");
        assertThat(dto.getType()).isEqualTo(TaskUpdateType.FAILED);
        assertThat(dto.getDescription()).isEqualTo("FAILED description");
        assertThat(dto.getModelType()).isEqualTo("TaskUpdate");
    }

    @Test
    void map_withNoTask_leavesTaskIdNull() {
        assertThat(this.mapper.map(taskUpdate("u1", TaskUpdateType.UPDATE)).getTaskId()).isNull();
    }

    @Test
    void map_withTask_emitsTaskAsShallowIdReference() {
        var update = taskUpdate("u1", TaskUpdateType.COMPLETED);
        var task = new TaskEntity();
        task.setId("t1");
        update.setTask(task);

        var dto = this.mapper.map(update);

        assertThat(dto.getTaskId()).isEqualTo("t1");
    }
}
