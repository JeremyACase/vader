package org.vader.core.server.models;

/**
 * A stored object's content, base64-encoded for transport in an MCP tool result.
 *
 * @param filename the original filename
 * @param contentType the stored MIME type, or {@code null} if never recorded
 * @param size the object's size in bytes, before base64 encoding
 * @param base64Content the object's raw bytes, base64-encoded
 */
public record EncodedObjectContent(
    String filename, String contentType, long size, String base64Content) {
}
