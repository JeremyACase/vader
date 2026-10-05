package org.vader.core.server.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.model.vader.entity.ObjectMetadataEntity;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.core.server.storage.interfaces.InterfaceFileStorageStrategy;
import org.vader.core.server.storage.model.ObjectUpload;

/**
 * Stores task agents' outputs and reads back any stored object, regardless of which
 * {@link InterfaceFileStorageStrategy} is active -- the upload tool, the REST download endpoint
 * and the MCP read tool all go through here rather than talking to a strategy directly, so none
 * of them has to know or care whether content actually lives in MinIO or the database.
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

    /**
     * Stores an object a task attempt produced and records which attempt produced it.
     *
     * @param upload the object to store
     * @param producedBy the task attempt uploading it
     * @return the stored object's id, filename, content type, and size
     */
    @Transactional
    public ObjectDescriptor storeTaskAttemptOutput(
        final ObjectUpload upload, final TaskAttemptEntity producedBy) {
        var metadata = this.storageStrategy.store(upload);
        metadata.setTaskAttempt(producedBy);
        var saved = this.repository.save(metadata);
        return new ObjectDescriptor(
            saved.getId(), saved.getOriginalFilename(), saved.getContentType(), saved.getSize());
    }

    /**
     * Describes every object one task attempt uploaded, without touching their content.
     *
     * @param taskAttemptId the uploading attempt's id
     * @return each upload's id, filename, content type, and size, oldest first
     */
    @Transactional(readOnly = true)
    public List<ObjectDescriptor> describeTaskAttemptOutputs(final String taskAttemptId) {
        return this.repository.findByTaskAttemptIdOrderByCreatedAtAsc(taskAttemptId).stream()
            .map(metadata -> new ObjectDescriptor(
                metadata.getId(),
                metadata.getOriginalFilename(),
                metadata.getContentType(),
                metadata.getSize()))
            .toList();
    }

    /**
     * Loads a text object's full content as a UTF-8 string. The caller is responsible for
     * checking the object is text and small enough to hold in memory.
     *
     * @param objectMetadataId the {@code ObjectMetadata} id
     * @return the object's content
     * @throws ObjectNotFoundException if no object exists with that id
     * @throws FileStorageException if the content could not be read
     */
    @Transactional(readOnly = true)
    public String retrieveText(final String objectMetadataId) {
        var resource = this.storageStrategy.retrieve(findOrThrow(objectMetadataId));
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new FileStorageException("Could not read object content as text", e);
        }
    }

    private ObjectMetadataEntity findOrThrow(final String objectMetadataId) {
        return this.repository.findById(objectMetadataId)
            .orElseThrow(() -> new ObjectNotFoundException(objectMetadataId));
    }
}
