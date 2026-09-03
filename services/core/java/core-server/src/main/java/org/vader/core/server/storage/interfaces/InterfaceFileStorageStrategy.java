package org.vader.core.server.storage.interfaces;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;
import org.vader.common.model.vader.entity.ObjectMetadataEntity;

/**
 * Strategy for persisting uploaded files and returning their storage metadata.
 *
 * <p>Active implementation is selected at startup via {@code vader.storage.type}. The database
 * strategy is the default and requires no additional infrastructure. The MinIO strategy requires
 * a running MinIO instance and its connection properties.</p>
 */
public interface InterfaceFileStorageStrategy {

    /**
     * Stores the supplied files and returns one metadata entity per file.
     *
     * <p>The returned entities are not yet persisted; callers are responsible for attaching them
     * to a {@link org.vader.common.model.vader.entity.ClientPromptEntity} before flushing.</p>
     *
     * @param files the uploaded files to store
     * @return a metadata entity for each stored file, in the same order as the input list
     */
    List<ObjectMetadataEntity> store(List<MultipartFile> files);
}
