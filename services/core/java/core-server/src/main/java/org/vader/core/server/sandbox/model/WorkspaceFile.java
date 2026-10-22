package org.vader.core.server.sandbox.model;

/**
 * One file a task attempt's sandbox workspace holds from the start -- attached to the client
 * prompt, or saved by an earlier task the attempt's task builds on -- paired with the name it is
 * staged under.
 *
 * @param objectMetadataId the stored object's {@code ObjectMetadata} id
 * @param filename the name the file is staged under, unique within the workspace
 * @param contentType the object's recorded MIME type, or {@code null} if never recorded
 * @param producedBy the title of the task whose attempt saved the file, or {@code null} for a
 *     file attached to the request
 */
public record WorkspaceFile(
    String objectMetadataId, String filename, String contentType, String producedBy) {

    /**
     * Whether an earlier task saved this file, rather than the client attaching it.
     *
     * @return {@code true} for a file a task saved
     */
    public boolean isUpstream() {
        return this.producedBy != null;
    }
}
