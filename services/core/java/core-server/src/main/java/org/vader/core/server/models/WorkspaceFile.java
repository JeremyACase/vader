package org.vader.core.server.models;

/**
 * One file attached to a client prompt, paired with the name it is staged under inside a task
 * attempt's sandbox workspace.
 *
 * @param objectMetadataId the stored object's {@code ObjectMetadata} id
 * @param filename the name the file is staged under, unique within the workspace
 * @param contentType the object's recorded MIME type, or {@code null} if never recorded
 */
public record WorkspaceFile(String objectMetadataId, String filename, String contentType) {
}
