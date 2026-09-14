package org.vader.core.server.service.strategies.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.model.vader.entity.ObjectMetadataEntity;

class MinioFileStorageStrategyTest {

    private MinioClient minioClient;
    private MinioFileStorageStrategy strategy;

    @BeforeEach
    void setUp() {
        this.minioClient = mock(MinioClient.class);
        this.strategy = new MinioFileStorageStrategy();
        ReflectionTestUtils.setField(this.strategy, "minioClient", this.minioClient);
        ReflectionTestUtils.setField(this.strategy, "bucket", "vader-files");
    }

    private static ObjectMetadataEntity metadata() {
        var entity = new ObjectMetadataEntity();
        entity.setBucketName("vader-files");
        entity.setObjectKey("11111111-1111-1111-1111-111111111111-diagram.png");
        entity.setOriginalFilename("diagram.png");
        entity.setSize(2048L);
        return entity;
    }

    @Test
    void retrieve_fetchesFromTheStoredBucketAndObjectKey() throws Exception {
        var metadata = metadata();
        var stream = mock(GetObjectResponse.class);
        when(this.minioClient.getObject(any(GetObjectArgs.class))).thenReturn(stream);

        var resource = this.strategy.retrieve(metadata);

        assertThat(resource.contentLength()).isEqualTo(2048L);
        assertThat(resource.getInputStream()).isSameAs(stream);

        var captor = ArgumentCaptor.forClass(GetObjectArgs.class);
        verify(this.minioClient).getObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("vader-files");
        assertThat(captor.getValue().object())
            .isEqualTo("11111111-1111-1111-1111-111111111111-diagram.png");
    }

    @Test
    void retrieve_whenMinioFailsThrowsFileStorageException() throws Exception {
        var metadata = metadata();
        when(this.minioClient.getObject(any(GetObjectArgs.class)))
            .thenThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> this.strategy.retrieve(metadata))
            .isInstanceOf(FileStorageException.class)
            .hasMessageContaining("diagram.png");
    }
}
