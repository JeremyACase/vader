package org.vader.core.server.service.tools.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Map;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.vader.core.server.exceptions.ObjectNotFoundException;
import org.vader.core.server.models.EncodedObjectContent;
import org.vader.core.server.service.storage.ObjectStorageService;
import org.vader.core.server.service.strategies.storage.FileStorageException;

/**
 * Exposes stored object content to LLMs as an MCP tool, transparently to whichever storage
 * strategy is active. Objects over the configured inline limit are rejected with a pointer to
 * the REST download endpoint instead, since base64-inlining an arbitrarily large object into a
 * tool result would blow the model's context budget.
 */
@Component
@ConditionalOnProperty(
    prefix = "vader.mcp.object-storage",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ObjectStorageTools {

    @Autowired
    private ObjectStorageService objectStorageService;

    @Value("${vader.mcp.object-storage.max-inline-bytes:2097152}")
    private long maxInlineBytes;

    /**
     * Fetches a previously-uploaded object's content, base64-encoded.
     *
     * @param objectMetadataId the {@code ObjectMetadata} id, from {@code query_object_metadata}
     *     or {@code get_object_metadata_by_id}
     * @return the encoded content, or {@code {"error": ...}} if unknown or too large
     */
    @Tool(
        name = "get_object_content",
        description = "Fetch a previously-uploaded object's raw content as base64, identified by "
            + "the id returned from query_object_metadata / get_object_metadata_by_id. Works the "
            + "same way regardless of whether the server is backed by MinIO or the database. "
            + "Objects larger than the configured inline limit are rejected with the REST "
            + "download URL to use instead.")
    public Object getObjectContent(
        @ToolParam(description = "The ObjectMetadata id, from query_object_metadata or "
            + "get_object_metadata_by_id.")
        final String objectMetadataId) {

        Object result;
        try {
            result = fetchOrReject(objectMetadataId);
        } catch (ObjectNotFoundException | FileStorageException e) {
            result = Map.of("error", String.valueOf(e.getMessage()));
        }
        return result;
    }

    private Object fetchOrReject(final String objectMetadataId) {
        var descriptor = this.objectStorageService.describe(objectMetadataId);
        Object result;
        if (descriptor.size() > this.maxInlineBytes) {
            result = Map.of(
                "error", "Object is " + descriptor.size() + " bytes, over the "
                    + this.maxInlineBytes + " byte inline limit. Download it directly from "
                    + "/vader/core-server/object-storage/" + objectMetadataId + "/content "
                    + "instead.");
        } else {
            var content = this.objectStorageService.retrieve(objectMetadataId);
            result = new EncodedObjectContent(
                content.filename(), content.contentType(), content.size(),
                base64Of(content.resource()));
        }
        return result;
    }

    private String base64Of(final Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            return Base64.getEncoder().encodeToString(in.readAllBytes());
        } catch (IOException e) {
            throw new FileStorageException("Could not read object content for base64 encoding", e);
        }
    }
}
