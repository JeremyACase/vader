package org.vader.core.server.service.strategies.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.vader.common.model.vader.entity.FileContentEntity;
import org.vader.common.model.vader.entity.ObjectMetadataEntity;

class DatabaseFileStorageStrategyTest {

    private final DatabaseFileStorageStrategy strategy = new DatabaseFileStorageStrategy();

    @Test
    void retrieve_returnsTheStoredBytes() throws Exception {
        var content = new FileContentEntity();
        content.setData("hello world".getBytes());
        var metadata = new ObjectMetadataEntity();
        metadata.setFileContent(content);

        var resource = this.strategy.retrieve(metadata);

        assertThat(resource.getInputStream().readAllBytes()).isEqualTo("hello world".getBytes());
        assertThat(resource.contentLength()).isEqualTo(11);
    }

    @Test
    void retrieve_withNoStoredContent_throwsFileStorageException() {
        var metadata = new ObjectMetadataEntity();
        metadata.setId("11111111-1111-1111-1111-111111111111");

        assertThatThrownBy(() -> this.strategy.retrieve(metadata))
            .isInstanceOf(FileStorageException.class)
            .hasMessageContaining(metadata.getId());
    }
}
