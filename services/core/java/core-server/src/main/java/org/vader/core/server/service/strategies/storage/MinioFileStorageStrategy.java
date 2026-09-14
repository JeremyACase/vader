package org.vader.core.server.service.strategies.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.vader.common.model.vader.entity.ObjectMetadataEntity;
import org.vader.core.server.service.strategies.storage.interfaces.InterfaceFileStorageStrategy;

/**
 * Stores uploaded file contents in a MinIO object store and persists only the metadata.
 *
 * <p>Active only when {@code vader.storage.type} is set to {@code minio}, in which case the Helm
 * chart also expects a MinIO deployment and its connection properties.</p>
 *
 * <p>Each file is uploaded under a UUID-prefixed object name to avoid collisions. The target
 * bucket is created on first use if it does not already exist.</p>
 */
@Component
@ConditionalOnProperty(prefix = "vader.storage", name = "type", havingValue = "minio")
public class MinioFileStorageStrategy implements InterfaceFileStorageStrategy {

    private static final Logger logger = LoggerFactory.getLogger(MinioFileStorageStrategy.class);

    @Autowired
    private MinioClient minioClient;

    @Value("${vader.storage.minio.bucket:vader-files}")
    private String bucket;

    @Override
    public List<ObjectMetadataEntity> store(final List<MultipartFile> files) {
        ensureBucketExists();
        return files.stream().map(this::uploadAndBuildMetadata).toList();
    }

    private void ensureBucketExists() {
        try {
            boolean exists = this.minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(this.bucket).build());
            if (!exists) {
                this.minioClient.makeBucket(
                    MakeBucketArgs.builder().bucket(this.bucket).build());
                logger.info("Created MinIO bucket '{}'", this.bucket);
            }
        } catch (Exception e) {
            throw new FileStorageException(
                "Could not verify or create MinIO bucket '" + this.bucket + "'", e);
        }
    }

    private ObjectMetadataEntity uploadAndBuildMetadata(final MultipartFile file) {
        String objectName = UUID.randomUUID() + "-" + file.getOriginalFilename();
        try {
            this.minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(this.bucket)
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        } catch (Exception e) {
            throw new FileStorageException(
                "Could not upload '" + file.getOriginalFilename() + "' to MinIO", e);
        }
        logger.info(
            "Uploaded '{}' as '{}' to MinIO bucket '{}'",
            file.getOriginalFilename(),
            objectName,
            this.bucket);

        var entity = new ObjectMetadataEntity();
        entity.setBucketName(this.bucket);
        entity.setObjectKey(objectName);
        entity.setOriginalFilename(file.getOriginalFilename());
        entity.setContentType(file.getContentType());
        entity.setSize(file.getSize());
        return entity;
    }

    @Override
    public Resource retrieve(final ObjectMetadataEntity metadata) {
        try {
            var stream = this.minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(metadata.getBucketName())
                    .object(metadata.getObjectKey())
                    .build());
            return new SizedInputStreamResource(stream, metadata.getSize());
        } catch (Exception e) {
            throw new FileStorageException(
                "Could not download '" + metadata.getOriginalFilename() + "' from MinIO", e);
        }
    }
}
