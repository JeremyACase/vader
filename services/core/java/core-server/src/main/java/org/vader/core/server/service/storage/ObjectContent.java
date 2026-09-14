package org.vader.core.server.service.storage;

import org.springframework.core.io.Resource;

/**
 * A previously stored object's full content plus the descriptive fields needed to serve it.
 *
 * <p>Internal to {@code core-server} -- {@link Resource} is not a clean JSON type, so this never
 * crosses the REST or MCP boundary directly. The REST controller streams {@link #resource()}
 * straight to the response body; the MCP tool base64-encodes it into its own payload type.</p>
 *
 * @param resource the object's complete content
 * @param filename the original filename, for {@code Content-Disposition}
 * @param contentType the stored MIME type, or {@code null} if never recorded
 * @param size the object's total size in bytes
 */
public record ObjectContent(Resource resource, String filename, String contentType, long size) {
}
