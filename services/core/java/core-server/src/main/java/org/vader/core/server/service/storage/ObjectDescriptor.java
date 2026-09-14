package org.vader.core.server.service.storage;

/**
 * The descriptive fields of a stored object, without its content -- cheap to fetch since it
 * never touches the backing store (MinIO or the database), only the {@code ObjectMetadata} row.
 *
 * @param id the {@code ObjectMetadata} id
 * @param filename the original filename
 * @param contentType the stored MIME type, or {@code null} if never recorded
 * @param size the object's total size in bytes
 */
public record ObjectDescriptor(String id, String filename, String contentType, long size) {
}
