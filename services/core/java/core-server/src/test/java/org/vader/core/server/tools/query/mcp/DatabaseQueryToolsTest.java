package org.vader.core.server.tools.query.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.vader.common.library.dao.model.QueryFilter;
import org.vader.core.server.tools.query.DatabaseQueryService;
import org.vader.core.server.tools.query.EntityDescription;
import org.vader.core.server.tools.query.QueryResult;

@ExtendWith(MockitoExtension.class)
class DatabaseQueryToolsTest {

    @Mock
    private DatabaseQueryService service;

    @InjectMocks
    private DatabaseQueryTools tools;

    @Test
    void listQueryableEntities_delegates() {
        var descriptions = List.of(new EntityDescription("Workflow", "d", List.of()));
        when(this.service.describe()).thenReturn(descriptions);

        assertThat(this.tools.listQueryableEntities()).isEqualTo(descriptions);
    }

    @Test
    void queryDatabase_delegatesAndReturnsTheResult() throws Exception {
        var result = new QueryResult(List.of(), 0, 0, 10, 0);
        when(this.service.query(any(), any(QueryFilter.class))).thenReturn(result);

        assertThat(this.tools.queryDatabase("Workflow", new QueryFilter())).isSameAs(result);
    }

    @Test
    void queryDatabase_onBadField_returnsErrorMap() throws Exception {
        when(this.service.query(any(), any(QueryFilter.class)))
            .thenThrow(new NoSuchFieldException("Field 'nope' not found on Workflow"));

        var response = this.tools.queryDatabase("Workflow", new QueryFilter());

        assertThat(response).isInstanceOfSatisfying(Map.class,
            map -> assertThat(map.get("error")).asString().contains("nope"));
    }

    @Test
    void countMatching_delegates() throws Exception {
        when(this.service.count(any(), any(QueryFilter.class))).thenReturn(42L);

        assertThat(this.tools.countMatching("Task", new QueryFilter()))
            .isEqualTo(Map.of("count", 42L));
    }
}
