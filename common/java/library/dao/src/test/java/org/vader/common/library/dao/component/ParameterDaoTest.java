package org.vader.common.library.dao.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.vader.common.library.dao.TestTaskRepository;
import org.vader.common.model.vader.entity.TaskEntity;

@SpringBootTest
class ParameterDaoTest {

    @Autowired
    private EntityDao<TaskEntity> entityDao;

    @Autowired
    private TestTaskRepository taskRepository;

    @BeforeEach
    void seed() {
        this.taskRepository.deleteAll();
        this.taskRepository.saveAll(List.of(
            task("Arrange food and cake"),
            task("Handle venue and decorations"),
            task("Set the date")));
    }

    @AfterEach
    void clean() {
        this.taskRepository.deleteAll();
    }

    private static TaskEntity task(final String title) {
        var task = new TaskEntity();
        task.setTitle(title);
        task.setDescription("d");
        return task;
    }

    private List<TaskEntity> query(final Map<String, String[]> params) throws Exception {
        return this.entityDao
            .getPage(params, 0, 50, true, List.of(), TaskEntity.class)
            .getContent();
    }

    @Test
    void exactMatch() throws Exception {
        assertThat(query(Map.of("title", new String[] {"Set the date"})))
            .extracting(TaskEntity::getTitle).containsExactly("Set the date");
    }

    @Test
    void likeWithTrailingStarOperator() throws Exception {
        assertThat(query(Map.of("title*", new String[] {"%and%"})))
            .extracting(TaskEntity::getTitle)
            .containsExactlyInAnyOrder("Arrange food and cake", "Handle venue and decorations");
    }

    @Test
    void nullTest() throws Exception {
        assertThat(query(Map.of("parentTask", new String[] {"null"}))).hasSize(3);
    }

    @Test
    void count() throws Exception {
        assertThat(this.entityDao.getCount(
            Map.of("title*", new String[] {"%venue%"}), TaskEntity.class)).isEqualTo(1);
    }

    @Test
    void unknownFieldThrows() {
        assertThatThrownBy(() -> query(Map.of("nope", new String[] {"x"})))
            .isInstanceOf(NoSuchFieldException.class);
    }
}
