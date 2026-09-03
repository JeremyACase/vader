package org.vader.common.library.implementation.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.common.model.vader.entity.TaskGraphEntity;

class TaskGraphDtoMapperTest {

    private final TaskGraphDtoMapper mapper = new TaskGraphDtoMapper();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(this.mapper, "taskDtoMapper", new TaskDtoMapper());
    }

    private static TaskEntity task(final String id) {
        var entity = new TaskEntity();
        entity.setId(id);
        entity.setTitle(id);
        entity.setDescription(id);
        return entity;
    }

    @Test
    void map_withNull_returnsNull() {
        assertThat(this.mapper.map((TaskGraphEntity) null)).isNull();
    }

    @Test
    void map_delegatesTaskMappingAndCopiesId() {
        var entity = new TaskGraphEntity();
        entity.setId("g1");
        entity.setTasks(new LinkedHashSet<>(List.of(task("t1"), task("t2"))));

        var dto = this.mapper.map(entity);

        assertThat(dto.getId()).isEqualTo("g1");
        assertThat(dto.getTasks()).extracting("id").containsExactly("t1", "t2");
        assertThat(dto.getModelType()).isEqualTo("TaskGraph");
    }

    @Test
    void map_withNoTasks_returnsEmptyList() {
        assertThat(this.mapper.map(new TaskGraphEntity()).getTasks()).isEmpty();
    }
}
