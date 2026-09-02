package org.vader.common.library.implementation.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.vader.common.model.vader.entity.ClientPromptEntity;

class ClientPromptDtoMapperTest {

    private final ClientPromptDtoMapper mapper = new ClientPromptDtoMapper();

    @Test
    void map_withNull_returnsNull() {
        assertThat(this.mapper.map((ClientPromptEntity) null)).isNull();
    }

    @Test
    void map_copiesTextAndIdentityFields() {
        var entity = new ClientPromptEntity();
        entity.setId("22222222-2222-2222-2222-222222222222");
        entity.setText("summarize this repo");

        var dto = this.mapper.map(entity);

        assertThat(dto.getId()).isEqualTo("22222222-2222-2222-2222-222222222222");
        assertThat(dto.getText()).isEqualTo("summarize this repo");
        assertThat(dto.getModelType()).isEqualTo("ClientPrompt");
    }

    @Test
    void map_leavesFilesEmpty_sinceMultipartFilesCannotBeRebuiltFromMetadata() {
        var entity = new ClientPromptEntity();
        entity.setText("has attachments");

        assertThat(this.mapper.map(entity).getFiles()).isEmpty();
    }
}
