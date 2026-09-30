package org.vader.core.server.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.vader.common.model.vader.entity.FileContentEntity;
import org.vader.common.model.vader.entity.ObjectMetadataEntity;
import org.vader.core.server.storage.interfaces.InterfaceFileStorageStrategy;
import org.vader.core.server.storage.model.ObjectUpload;

/**
 * Persists object contents as BLOBs in the relational database.
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
    public ObjectMetadataEntity store(final ObjectUpload upload) {
        var content = new FileContentEntity();
        content.setData(readAllBytes(upload));

        var metadata = new ObjectMetadataEntity();
        metadata.setOriginalFilename(upload.filename());
        metadata.setContentType(upload.contentType());
        metadata.setSize(upload.size());
        metadata.setFileContent(content);

        logger.debug(
            "Staged '{}' ({} bytes) for database BLOB storage", upload.filename(), upload.size());
        return metadata;
    }

    @Override
    public Resource retrieve(final ObjectMetadataEntity metadata) {
        var content = metadata.getFileContent();
        if (Objects.isNull(content) || Objects.isNull(content.getData())) {
            throw new FileStorageException(
                "No database content stored for object '" + metadata.getId() + "'", null);
        }
        return new ByteArrayResource(content.getData());
    }

    private static byte[] readAllBytes(final ObjectUpload upload) {
        try (InputStream in = upload.content().getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new FileStorageException("Could not read '" + upload.filename() + "'", e);
        }
    }
}
