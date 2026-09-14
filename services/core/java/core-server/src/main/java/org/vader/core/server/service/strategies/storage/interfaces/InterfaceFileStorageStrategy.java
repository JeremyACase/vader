package org.vader.core.server.service.strategies.storage.interfaces;

import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import org.vader.common.model.vader.entity.ObjectMetadataEntity;

/**
 * Strategy for persisting uploaded files, and reading them back, hiding where they actually
 * live.
 *
 * <p>Active implementation is selected at startup via {@code vader.storage.type}. The database
 * strategy is the default and requires no additional infrastructure. The MinIO strategy requires
 * a running MinIO instance and its connection properties. Callers of either method -- the object
 * storage REST endpoint and its MCP tool -- never need to know which one is active.</p>
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

    /**
     * Loads the complete content of a previously stored object.
     *
     * <p>The returned {@link Resource} always covers the whole object; callers that only need a
     * byte range (e.g. an HTTP {@code Range} request) slice it themselves rather than asking the
     * strategy for a partial fetch, so both implementations stay simple.</p>
     *
     * @param metadata the object's persisted metadata, as stored by {@link #store}
     * @return a {@link Resource} streaming the object's full content
     */
    Resource retrieve(ObjectMetadataEntity metadata);
}
