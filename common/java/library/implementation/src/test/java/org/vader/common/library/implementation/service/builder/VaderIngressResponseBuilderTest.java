package org.vader.common.library.implementation.service.builder;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.vader.common.model.vader.entity.ClientPromptEntity;

class VaderIngressResponseBuilderTest {

    private final VaderIngressResponseBuilder builder = new VaderIngressResponseBuilder();

    @Test
    void buildIngressResponseFrom_copiesIdAndPayloadModelTypeFromTheEntity() {
        var entity = new ClientPromptEntity();
        entity.setId("aaaaaaaa-1111-2222-3333-444444444444");
        entity.setText("decompose this");

        var response = this.builder.buildIngressResponseFrom(entity);

        assertThat(response.getId()).isEqualTo("aaaaaaaa-1111-2222-3333-444444444444");
        assertThat(response.getModelType()).isEqualTo("IngressResponse");
        assertThat(response.getPayloadModelType()).isEqualTo("ClientPrompt");
    }
}
