package org.vader.core.server.service.storage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.model.vader.entity.ObjectMetadataEntity;
import org.vader.core.exceptions.ObjectNotFoundException;
import org.vader.core.server.repository.ObjectMetadataRepository;
import org.vader.core.server.service.strategies.storage.interfaces.InterfaceFileStorageStrategy;

/**
 * Reads back objects that were previously stored via {@link InterfaceFileStorageStrategy},
 * regardless of which storage strategy is active -- the REST download endpoint and the MCP tool
 * both go through here rather than talking to a strategy directly, so neither has to know or
 * care whether content actually lives in MinIO or the database.
 */
@Service
public class ObjectStorageService {

    @Autowired
    private ObjectMetadataRepository repository;

    @Autowired
    private InterfaceFileStorageStrategy storageStrategy;

    /**
     * Looks up an object's descriptive fields without touching its content.
     *
     * @param objectMetadataId the {@code ObjectMetadata} id
     * @return the object's filename, content type, and size
     * @throws ObjectNotFoundException if no object exists with that id
     */
    @Transactional(readOnly = true)
    public ObjectDescriptor describe(final String objectMetadataId) {
        var metadata = findOrThrow(objectMetadataId);
        return new ObjectDescriptor(
            metadata.getId(),
            metadata.getOriginalFilename(),
            metadata.getContentType(),
            metadata.getSize());
    }

    /**
     * Loads an object's full content, via whichever storage strategy is active.
     *
     * @param objectMetadataId the {@code ObjectMetadata} id
     * @return the object's content and descriptive fields
     * @throws ObjectNotFoundException if no object exists with that id
     */
    @Transactional(readOnly = true)
    public ObjectContent retrieve(final String objectMetadataId) {
        var metadata = findOrThrow(objectMetadataId);
        var resource = this.storageStrategy.retrieve(metadata);
        return new ObjectContent(
            resource, metadata.getOriginalFilename(), metadata.getContentType(),
            metadata.getSize());
    }

    private ObjectMetadataEntity findOrThrow(final String objectMetadataId) {
        return this.repository.findById(objectMetadataId)
            .orElseThrow(() -> new ObjectNotFoundException(objectMetadataId));
    }
}
