package org.vader.core.server.storage;

import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.vader.common.model.vader.entity.FileContentEntity;
import org.vader.common.model.vader.entity.ObjectMetadataEntity;
import org.vader.core.server.storage.interfaces.InterfaceFileStorageStrategy;

/**
 * Persists uploaded file contents as BLOBs in the relational database.
 *
 * <p>Active when {@code vader.storage.type} is {@code database}, or when the property is absent
 * (i.e. {@code matchIfMissing = true} makes this the default). No additional infrastructure is
 * required beyond the database already used by the application.</p>
 */
@Component
@ConditionalOnProperty(
    prefix = "vader.storage",
    name = "type",
    havingValue = "database",
    matchIfMissing = true)
public class DatabaseFileStorageStrategy implements InterfaceFileStorageStrategy {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseFileStorageStrategy.class);

    @Override
    public List<ObjectMetadataEntity> store(final List<MultipartFile> files) {
        return files.stream().map(this::toEntity).toList();
    }

    private ObjectMetadataEntity toEntity(final MultipartFile file) {
        var content = new FileContentEntity();
        try {
            content.setData(file.getBytes());
        } catch (IOException e) {
            throw new FileStorageException(
                "Could not read uploaded file: " + file.getOriginalFilename(), e);
        }

        var metadata = new ObjectMetadataEntity();
        metadata.setOriginalFilename(file.getOriginalFilename());
        metadata.setContentType(file.getContentType());
        metadata.setSize(file.getSize());
        metadata.setFileContent(content);

        logger.debug(
            "Staged '{}' ({} bytes) for database BLOB storage",
            file.getOriginalFilename(),
            file.getSize());
        return metadata;
    }
}
