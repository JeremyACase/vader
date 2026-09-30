package org.vader.core.server.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.vader.common.model.vader.entity.FileContentEntity;
import org.vader.common.model.vader.entity.ObjectMetadataEntity;
import org.vader.core.server.storage.model.ObjectUpload;

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

    @Test
    void store_keepsTheBytesInTheDatabaseAlongsideTheMetadata() {
        var upload = new ObjectUpload(
            "report.md", "text/markdown", 5, new ByteArrayResource("hello".getBytes()));

        var metadata = this.strategy.store(upload);

        assertThat(metadata.getFileContent().getData()).isEqualTo("hello".getBytes());
        assertThat(metadata.getOriginalFilename()).isEqualTo("report.md");
        assertThat(metadata.getContentType()).isEqualTo("text/markdown");
        assertThat(metadata.getSize()).isEqualTo(5L);
        assertThat(metadata.getObjectKey()).isNull();
    }
}
