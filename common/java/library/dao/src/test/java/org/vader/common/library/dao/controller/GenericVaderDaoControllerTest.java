package org.vader.common.library.dao.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.library.dao.component.EntityDao;
import org.vader.common.library.dao.model.QueryFilter;
import org.vader.common.library.dao.service.PageValidator;
import org.vader.common.library.implementation.interfaces.mapper.InterfaceEntityToDtoMapper;
import org.vader.common.model.vader.dto.Task;
import org.vader.common.model.vader.entity.TaskEntity;

class GenericVaderDaoControllerTest {

    @SuppressWarnings("unchecked")
    private final EntityDao<TaskEntity> entityDao = mock(EntityDao.class);

    @SuppressWarnings("unchecked")
    private final InterfaceEntityToDtoMapper<TaskEntity, Task> mapper =
        mock(InterfaceEntityToDtoMapper.class);

    private TestController controller;

    @BeforeEach
    void setUp() {
        this.controller = new TestController();
        var pageValidator = new PageValidator();
        ReflectionTestUtils.setField(pageValidator, "maxPageSize", 100);
        ReflectionTestUtils.setField(this.controller, "pageValidator", pageValidator);
        ReflectionTestUtils.setField(this.controller, "objectMapper", new ObjectMapper());
    }

    @Test
    void constructor_cachesEntityAndDtoClasses() {
        assertThat(this.controller.getEntityClass()).isEqualTo(TaskEntity.class);
        assertThat(this.controller.getDtoClass()).isEqualTo(Task.class);
    }

    @Test
    void query_mapsThePageToDtos() throws Exception {
        var entity = new TaskEntity();
        var dto = new Task();
        when(this.entityDao.getPage(
                any(QueryFilter.class), anyInt(), anyInt(), eq(TaskEntity.class)))
            .thenReturn(new PageImpl<>(List.of(entity), PageRequest.of(0, 10), 1));
        when(this.mapper.map(List.of(entity))).thenReturn(List.of(dto));

        var page = this.controller.query(new QueryFilter());

        assertThat(page.getContent()).containsExactly(dto);
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void count_returnsCountMap() throws Exception {
        when(this.entityDao.getCount(any(QueryFilter.class), eq(TaskEntity.class))).thenReturn(7L);

        assertThat(this.controller.count(new QueryFilter())).containsEntry("count", 7L);
    }

    @Test
    void queryById_whenPresent_returns200() throws Exception {
        var dto = new Task();
        when(this.entityDao.getPage(any(), anyInt(), anyInt(), any(), any(), eq(TaskEntity.class)))
            .thenReturn(new PageImpl<>(List.of(new TaskEntity()), PageRequest.of(0, 1), 1));
        when(this.mapper.map(any(List.class))).thenReturn(List.of(dto));

        var response = this.controller.queryById("some-id");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(dto);
    }

    @Test
    void queryById_whenAbsent_returns204() throws Exception {
        when(this.entityDao.getPage(any(), anyInt(), anyInt(), any(), any(), eq(TaskEntity.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 1), 0));

        assertThat(this.controller.queryById("missing").getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);
    }

    private final class TestController extends GenericVaderDaoController<TaskEntity, Task> {

        @Override
        public EntityDao<TaskEntity> getDataAccessObject() {
            return entityDao;
        }

        @Override
        public InterfaceEntityToDtoMapper<TaskEntity, Task> getDataTransferObjectMapper() {
            return mapper;
        }
    }
}
