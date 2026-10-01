package org.vader.common.library.implementation.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskAttemptReviewOutboxMessageEntity;

class TaskAttemptReviewOutboxMessageDtoMapperTest {

    private final TaskAttemptReviewOutboxMessageDtoMapper mapper =
        new TaskAttemptReviewOutboxMessageDtoMapper();

    @Test
    void map_withNull_returnsNull() {
        assertThat(this.mapper.map((TaskAttemptReviewOutboxMessageEntity) null)).isNull();
    }

    @Test
    void map_emitsAttemptIdAndNextAttemptAt() {
        var nextAttemptAt = OffsetDateTime.parse("2026-01-01T00:00:30Z");
        var attempt = new TaskAttemptEntity();
        attempt.setId("a1");
        var entity = new TaskAttemptReviewOutboxMessageEntity();
        entity.setTaskAttempt(attempt);
        entity.setNextAttemptAt(nextAttemptAt);

        var dto = this.mapper.map(entity);

        assertThat(dto.getTaskAttemptId()).isEqualTo("a1");
        assertThat(dto.getNextAttemptAt()).isEqualTo(nextAttemptAt);
    }
}
