package org.vader.core.server.tools.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.library.dao.controller.GenericVaderDaoController;
import org.vader.common.library.dao.model.QueryFilter;
import org.vader.common.model.vader.dto.Task;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.core.server.controller.dao.WorkflowDaoController;

class DatabaseQueryServiceTest {

    private VaderDaoRegistry registry;
    private DatabaseQueryService service;

    @BeforeEach
    void setUp() {
        this.registry = mock(VaderDaoRegistry.class);
        this.service = new DatabaseQueryService();
        ReflectionTestUtils.setField(this.service, "registry", this.registry);
    }

    @Test
    void describe_reflectsEntityFieldsAndSkipsModelType() {
        var realRegistry = new VaderDaoRegistry();
        ReflectionTestUtils.setField(
            realRegistry, "controllers", List.of(new WorkflowDaoController()));
        ReflectionTestUtils.invokeMethod(realRegistry, "index");
        ReflectionTestUtils.setField(this.service, "registry", realRegistry);

        var descriptions = this.service.describe();

        assertThat(descriptions).hasSize(1);
        var workflow = descriptions.get(0);
        assertThat(workflow.name()).isEqualTo("Workflow");
        assertThat(workflow.fields()).extracting(EntityDescription.Field::name)
            .contains("id", "createdAt", "clientPrompt", "taskPlan")
            .doesNotContain("modelType");
        assertThat(workflow.fields())
            .filteredOn(field -> field.name().equals("taskPlan"))
            .extracting(EntityDescription.Field::type)
            .containsExactly("-> TaskPlan");
    }

    @Test
    @SuppressWarnings("unchecked")
    void query_delegatesToTheControllerAndTrimsThePage() throws Exception {
        var controller = mock(GenericVaderDaoController.class);
        var dto = new Task();
        when(this.registry.require("Task")).thenReturn(controller);
        when(controller.query(any(QueryFilter.class)))
            .thenReturn(new org.vader.common.library.dao.model.GenericPageImplementation<>(
                List.of(dto), 0, 10, 1L, null, true, 1, null, true, 1, false));

        var result = this.service.query("Task", new QueryFilter());

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0)).isSameAs(dto);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void count_delegatesToTheController() throws Exception {
        var controller = mock(GenericVaderDaoController.class);
        when(this.registry.require("Task")).thenReturn(controller);
        when(controller.count(any(QueryFilter.class))).thenReturn(Map.of("count", 5L));

        assertThat(this.service.count("Task", new QueryFilter())).isEqualTo(5L);
    }

    @Test
    void query_unknownEntity_propagatesIllegalArgument() {
        when(this.registry.require("Nope"))
            .thenThrow(new IllegalArgumentException("Unknown entity 'Nope'."));

        assertThatThrownBy(() -> this.service.query("Nope", new QueryFilter()))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
