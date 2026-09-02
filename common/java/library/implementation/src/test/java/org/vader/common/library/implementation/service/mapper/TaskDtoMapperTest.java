package org.vader.common.library.implementation.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.vader.common.model.vader.entity.TaskEntity;

class TaskDtoMapperTest {

    private final TaskDtoMapper mapper = new TaskDtoMapper();

    private static TaskEntity task(final String id, final String title) {
        var entity = new TaskEntity();
        entity.setId(id);
        entity.setTitle(title);
        entity.setDescription(title + " description");
        return entity;
    }

    @Test
    void map_withNull_returnsNull() {
        assertThat(this.mapper.map((TaskEntity) null)).isNull();
    }

    @Test
    void map_copiesScalarFields() {
        var dto = this.mapper.map(task("t1", "root"));

        assertThat(dto.getId()).isEqualTo("t1");
        assertThat(dto.getTitle()).isEqualTo("root");
        assertThat(dto.getDescription()).isEqualTo("root description");
        assertThat(dto.getModelType()).isEqualTo("Task");
    }

    @Test
    void map_withNoParent_leavesParentTaskIdNull() {
        assertThat(this.mapper.map(task("t1", "root")).getParentTaskId()).isNull();
    }

    @Test
    void map_withParent_emitsParentAsShallowIdReference() {
        var child = task("t2", "child");
        child.setParentTask(task("t1", "root"));

        var dto = this.mapper.map(child);

        assertThat(dto.getParentTaskId()).isEqualTo("t1");
    }

    @Test
    void map_recursesIntoSubTasks() {
        var root = task("t1", "root");
        var childA = task("t2", "childA");
        var childB = task("t3", "childB");
        root.setSubTasks(new LinkedHashSet<>(List.of(childA, childB)));

        var dto = this.mapper.map(root);

        assertThat(dto.getSubTasks()).extracting("id").containsExactly("t2", "t3");
        assertThat(dto.getSubTasks()).extracting("title").containsExactly("childA", "childB");
    }

    @Test
    void map_emitsDependenciesAsShallowIdReferences() {
        var task = task("t3", "fan-in");
        task.setDependsOn(new LinkedHashSet<>(List.of(task("t1", "a"), task("t2", "b"))));

        var dto = this.mapper.map(task);

        assertThat(dto.getDependsOnTaskIds()).containsExactly("t1", "t2");
    }

    @Test
    void map_withNoDependencies_returnsEmptyList() {
        assertThat(this.mapper.map(task("t1", "root")).getDependsOnTaskIds()).isEmpty();
    }
}
