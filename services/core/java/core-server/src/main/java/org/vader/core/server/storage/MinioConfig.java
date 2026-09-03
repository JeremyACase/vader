package org.vader.core.server.storage;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the MinIO client bean when {@code vader.storage.type} is {@code minio}.
 *
 * <p>Separating client construction from the strategy makes the client injectable in tests
 * without needing to spin up a real MinIO server.</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "vader.storage", name = "type", havingValue = "minio")
public class MinioConfig {

    @Bean
    public MinioClient minioClient(
        @Value("${vader.storage.minio.endpoint}") final String endpoint,
        @Value("${vader.storage.minio.access-key}") final String accessKey,
        @Value("${vader.storage.minio.secret-key}") final String secretKey) {

        return MinioClient.builder()
            .endpoint(endpoint)
            .credentials(accessKey, secretKey)
            .build();
    }
}
