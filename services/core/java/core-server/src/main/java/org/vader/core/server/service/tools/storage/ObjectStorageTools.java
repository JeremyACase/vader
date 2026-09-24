package org.vader.core.server.service.tools.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
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
 *
 * <p>Content type is a hard gate, not just a tool-description suggestion: a model deciding
 * whether to call this tool or {@code stage_object} is guidance it can simply ignore, so
 * anything that isn't recognizably text is refused here before it is ever read off disk, let
 * alone base64-encoded into a result the model will see.</p>
 */
@Component
@ConditionalOnProperty(
    prefix = "vader.mcp.object-storage",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ObjectStorageTools {

    private static final Set<String> ADDITIONAL_TEXT_CONTENT_TYPES = Set.of(
        "application/json", "application/xml", "application/x-yaml", "application/yaml");

    @Autowired
    private ObjectStorageService objectStorageService;

    @Value("${vader.mcp.object-storage.max-inline-bytes:2097152}")
    private long maxInlineBytes;

    /**
     * Fetches a previously-uploaded object's content, base64-encoded.
     *
     * @param objectMetadataId the {@code ObjectMetadata} id, from {@code query_object_metadata}
     *     or {@code get_object_metadata_by_id}
     * @return the encoded content, or {@code {"error": ...}} if unknown, not text, or too large
     */
    @Tool(
        name = "get_object_content",
        description = "Fetch a previously-uploaded TEXT object's raw content as base64, inlined "
            + "directly into this conversation, identified by the id returned from "
            + "query_object_metadata / get_object_metadata_by_id. This tool refuses anything "
            + "whose recorded content type isn't text -- spreadsheets, images, and every other "
            + "binary format are rejected outright, not merely discouraged. A file attached to "
            + "the request you are working on is already in your Python working directory: open "
            + "it by filename with run_python_code instead. Works the "
            + "same way regardless of whether the server is backed by MinIO or the database. "
            + "Objects larger than the configured inline limit are also rejected, with the REST "
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
        if (!isTextContentType(descriptor.contentType())) {
            result = Map.of(
                "error", "'" + descriptor.filename() + "' has content type '"
                    + descriptor.contentType() + "', which get_object_content refuses to inline "
                    + "into this conversation. If it is attached to the request you are working "
                    + "on, it is already in your Python working directory as '"
                    + descriptor.filename() + "' (or the name your task context gives) -- "
                    + "open it with run_python_code instead.");
        } else if (descriptor.size() > this.maxInlineBytes) {
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

    private static boolean isTextContentType(final String contentType) {
        return contentType != null
            && (contentType.startsWith("text/")
                || ADDITIONAL_TEXT_CONTENT_TYPES.contains(contentType));
    }

    private String base64Of(final Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            return Base64.getEncoder().encodeToString(in.readAllBytes());
        } catch (IOException e) {
            throw new FileStorageException("Could not read object content for base64 encoding", e);
        }
    }
}
