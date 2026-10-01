package org.vader.common.library.implementation.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.vader.common.model.vader.entity.LlmRequestKind;
import org.vader.common.model.vader.entity.LlmRequestOutboxMessageEntity;
import org.vader.common.model.vader.entity.OutboxMessageStatus;

class LlmRequestOutboxMessageDtoMapperTest {

    private final LlmRequestOutboxMessageDtoMapper mapper = new LlmRequestOutboxMessageDtoMapper();

    @Test
    void map_withNull_returnsNull() {
        assertThat(this.mapper.map((LlmRequestOutboxMessageEntity) null)).isNull();
    }

    @Test
    void map_copiesLifecycleFields() {
        var claimedAt = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        var entity = new LlmRequestOutboxMessageEntity();
        entity.setId("m1");
        entity.setStatus(OutboxMessageStatus.FAILED);
        entity.setClaimedAt(claimedAt);
        entity.setProcessedAt(claimedAt.plusSeconds(5));
        entity.setAttempts(2);
        entity.setFailureReason("boom");

        var dto = this.mapper.map(entity);

        assertThat(dto.getId()).isEqualTo("m1");
        assertThat(dto.getStatus()).isEqualTo(OutboxMessageStatus.FAILED);
        assertThat(dto.getClaimedAt()).isEqualTo(claimedAt);
        assertThat(dto.getProcessedAt()).isEqualTo(claimedAt.plusSeconds(5));
        assertThat(dto.getAttempts()).isEqualTo(2);
        assertThat(dto.getFailureReason()).isEqualTo("boom");
        assertThat(dto.getModelType()).isEqualTo("LlmRequestOutboxMessage");
    }

    @Test
    void map_copiesKindRequestAndResponse() {
        var entity = new LlmRequestOutboxMessageEntity();
        entity.setKind(LlmRequestKind.EVALUATION);
        entity.setRequestJson("{\"q\":1}");
        entity.setResponseJson("{\"a\":2}");

        var dto = this.mapper.map(entity);

        assertThat(dto.getKind()).isEqualTo(LlmRequestKind.EVALUATION);
        assertThat(dto.getRequestJson()).isEqualTo("{\"q\":1}");
        assertThat(dto.getResponseJson()).isEqualTo("{\"a\":2}");
    }
}
