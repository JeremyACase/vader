package org.vader.common.library.implementation.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.vader.common.model.vader.entity.TaskAssignmentOutboxMessageEntity;
import org.vader.common.model.vader.entity.TaskAttemptEntity;

class TaskAssignmentOutboxMessageDtoMapperTest {

    private final TaskAssignmentOutboxMessageDtoMapper mapper =
        new TaskAssignmentOutboxMessageDtoMapper();

    @Test
    void map_withNull_returnsNull() {
        assertThat(this.mapper.map((TaskAssignmentOutboxMessageEntity) null)).isNull();
    }

    @Test
    void map_emitsAttemptAsShallowIdReference() {
        var attempt = new TaskAttemptEntity();
        attempt.setId("a1");
        var entity = new TaskAssignmentOutboxMessageEntity();
        entity.setTaskAttempt(attempt);

        assertThat(this.mapper.map(entity).getTaskAttemptId()).isEqualTo("a1");
    }
}
