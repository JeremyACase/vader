package org.vader.common.library.implementation.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.vader.common.model.vader.entity.ClientPromptEntity;
import org.vader.common.model.vader.entity.ClientPromptOutboxMessageEntity;
import org.vader.common.model.vader.entity.OutboxMessageStatus;

class ClientPromptOutboxMessageDtoMapperTest {

    private final ClientPromptOutboxMessageDtoMapper mapper =
        new ClientPromptOutboxMessageDtoMapper();

    @Test
    void map_withNull_returnsNull() {
        assertThat(this.mapper.map((ClientPromptOutboxMessageEntity) null)).isNull();
    }

    @Test
    void map_emitsPromptAsShallowIdReference() {
        var prompt = new ClientPromptEntity();
        prompt.setId("p1");
        var entity = new ClientPromptOutboxMessageEntity();
        entity.setStatus(OutboxMessageStatus.CLAIMED);
        entity.setClientPrompt(prompt);

        var dto = this.mapper.map(entity);

        assertThat(dto.getClientPromptId()).isEqualTo("p1");
        assertThat(dto.getStatus()).isEqualTo(OutboxMessageStatus.CLAIMED);
    }

    @Test
    void map_withNoPrompt_leavesPromptIdNull() {
        assertThat(this.mapper.map(new ClientPromptOutboxMessageEntity()).getClientPromptId())
            .isNull();
    }
}
