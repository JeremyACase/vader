package org.vader.core.server.storage.model;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * One object to store, independent of where it came from -- a client's multipart upload or a
 * file a task agent wrote in its sandbox -- so {@code InterfaceFileStorageStrategy} never needs
 * to know its caller.
 *
 * @param filename the name to record as the object's original filename
 * @param contentType the MIME type to record, or {@code null} if unknown
 * @param size the content's total size in bytes
 * @param content the bytes to store, read exactly once
 */
public record ObjectUpload(String filename, String contentType, long size, Resource content) {

    /**
     * Adapts a client's multipart upload.
     *
     * @param file the uploaded file
     * @return the object to store
     */
    public static ObjectUpload of(final MultipartFile file) {
        return new ObjectUpload(
            file.getOriginalFilename(), file.getContentType(), file.getSize(), file.getResource());
    }
}
