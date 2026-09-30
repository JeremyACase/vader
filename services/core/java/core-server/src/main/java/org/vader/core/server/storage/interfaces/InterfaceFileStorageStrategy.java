package org.vader.core.server.storage.interfaces;

import org.springframework.core.io.Resource;
import org.vader.common.model.vader.entity.ObjectMetadataEntity;
import org.vader.core.server.storage.model.ObjectUpload;

/**
 * Strategy for persisting objects, and reading them back, hiding where they actually live.
 *
 * <p>Active implementation is selected at startup via {@code vader.storage.type}. The database
 * strategy is the default and requires no additional infrastructure. The MinIO strategy requires
 * a running MinIO instance and its connection properties. Callers -- prompt intake, a task
 * agent's upload, the object storage REST endpoint and its MCP tool -- never need to know which
 * one is active.</p>
 */
public interface InterfaceFileStorageStrategy {

    /**
     * Stores one object's content and returns its metadata entity.
     *
     * <p>The returned entity is not yet persisted; callers attach it to its owner (a client
     * prompt, or the task attempt that produced it) and save it themselves.</p>
     *
     * @param upload the object to store
     * @return the metadata entity for the stored object
     */
    ObjectMetadataEntity store(ObjectUpload upload);

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
