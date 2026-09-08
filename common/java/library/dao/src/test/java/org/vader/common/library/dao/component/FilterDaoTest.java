package org.vader.common.library.dao.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.vader.common.library.dao.TestTaskRepository;
import org.vader.common.library.dao.model.QueryFilter;
import org.vader.common.library.dao.model.QueryFilterParameter;
import org.vader.common.library.dao.model.QueryOperatorType;
import org.vader.common.library.dao.model.SortType;
import org.vader.common.model.vader.entity.TaskEntity;

@SpringBootTest
class FilterDaoTest {

    @Autowired
    private EntityDao<TaskEntity> entityDao;

    @Autowired
    private TestTaskRepository taskRepository;

    @BeforeEach
    void seed() {
        this.taskRepository.deleteAll();
        this.taskRepository.saveAll(List.of(
            task("Set the date and guest list", "Pick a date and invite friends."),
            task("Arrange food and cake", "Order a cake and decide on snacks."),
            task("Handle venue and decorations", "Prepare the space.")));
    }

    @AfterEach
    void clean() {
        this.taskRepository.deleteAll();
    }

    private static TaskEntity task(final String title, final String description) {
        var task = new TaskEntity();
        task.setTitle(title);
        task.setDescription(description);
        return task;
    }

    private static QueryFilter filter(final QueryFilterParameter... parameters) {
        var queryFilter = new QueryFilter();
        queryFilter.setParameters(List.of(parameters));
        queryFilter.setPageSize(50);
        return queryFilter;
    }

    private static QueryFilterParameter parameter(
        final String key, final QueryOperatorType operator, final String value) {
        var parameter = new QueryFilterParameter();
        parameter.setKey(key);
        parameter.setOperator(operator);
        parameter.setValue(value);
        return parameter;
    }

    @Test
    void getPage_equalOnTitle_returnsTheExactMatch() throws Exception {
        var page = this.entityDao.getPage(
            filter(parameter("title", QueryOperatorType.EQUAL, "Arrange food and cake")),
            0, 50, TaskEntity.class);

        assertThat(page.getContent()).extracting(TaskEntity::getTitle)
            .containsExactly("Arrange food and cake");
    }

    @Test
    void getPage_likeOnTitle_returnsAllPartialMatches() throws Exception {
        var page = this.entityDao.getPage(
            filter(parameter("title", QueryOperatorType.LIKE, "%and%")),
            0, 50, TaskEntity.class);

        assertThat(page.getContent()).extracting(TaskEntity::getTitle)
            .containsExactlyInAnyOrder(
                "Set the date and guest list",
                "Arrange food and cake",
                "Handle venue and decorations");
    }

    @Test
    void getPage_nullOnParentTask_matchesRootTasks() throws Exception {
        var page = this.entityDao.getPage(
            filter(parameter("parentTask", QueryOperatorType.EQUAL, "null")),
            0, 50, TaskEntity.class);

        assertThat(page.getContent()).hasSize(3);
    }

    @Test
    void getPage_sortsAndPaginates() throws Exception {
        var queryFilter = filter();
        queryFilter.setSortBy(List.of("title"));
        queryFilter.setSort(SortType.ASCENDING);
        queryFilter.setPageSize(2);

        var first = this.entityDao.getPage(queryFilter, 0, 2, TaskEntity.class);
        var second = this.entityDao.getPage(queryFilter, 1, 2, TaskEntity.class);

        assertThat(first.getContent()).extracting(TaskEntity::getTitle)
            .containsExactly("Arrange food and cake", "Handle venue and decorations");
        assertThat(second.getContent()).extracting(TaskEntity::getTitle)
            .containsExactly("Set the date and guest list");
        assertThat(first.getTotalElements()).isEqualTo(3);
    }

    @Test
    void getCount_countsMatchesWithoutFetching() throws Exception {
        var count = this.entityDao.getCount(
            filter(parameter("title", QueryOperatorType.LIKE, "%venue%")), TaskEntity.class);

        assertThat(count).isEqualTo(1);
    }

    @Test
    void getPage_unknownField_throwsNoSuchField() {
        assertThatThrownBy(() -> this.entityDao.getPage(
            filter(parameter("nope", QueryOperatorType.EQUAL, "x")), 0, 50, TaskEntity.class))
            .isInstanceOf(NoSuchFieldException.class);
    }
}
