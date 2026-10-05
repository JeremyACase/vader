package org.vader.core.server.storage;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.util.StringUtils;

/**
 * Infers a stored object's content type from its filename, for an upload that arrives with none
 * of its own (e.g. a file a task agent wrote in its sandbox).
 *
 * <p>Spring's own table has no entry for a few text formats agents commonly write, so those are
 * filled in here; without them a Markdown report would be recorded as
 * {@code application/octet-stream}, which {@code get_object_content} then refuses to read back as
 * text.</p>
 *
 * <p>Also the one definition of which content types count as text, so the MCP read tool and the
 * workflow's final answer agree on what may be inlined.</p>
 */
public final class ObjectContentTypeResolver {

    private static final Map<String, String> MISSING_FROM_SPRING = Map.of(
        "md", "text/markdown",
        "markdown", "text/markdown",
        "py", "text/x-python",
        "yaml", "application/yaml",
        "yml", "application/yaml");

    private static final Set<String> ADDITIONAL_TEXT_CONTENT_TYPES = Set.of(
        "application/json", "application/xml", "application/x-yaml", "application/yaml");

    private ObjectContentTypeResolver() {
    }

    /**
     * Resolves a filename to a content type, falling back to {@code application/octet-stream}.
     *
     * @param filename the object's filename
     * @return the inferred MIME type, never {@code null}
     */
    public static String forFilename(final String filename) {
        var extension = Optional.ofNullable(StringUtils.getFilenameExtension(filename))
            .map(value -> value.toLowerCase(Locale.ROOT))
            .orElse("");
        return Optional.ofNullable(MISSING_FROM_SPRING.get(extension))
            .or(() -> MediaTypeFactory.getMediaType(filename).map(MediaType::toString))
            .orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE);
    }

    /**
     * Whether a content type is text, and so safe to inline as a string.
     *
     * @param contentType the stored MIME type, possibly {@code null}
     * @return {@code true} for any {@code text/*} type or a known text-based application type
     */
    public static boolean isText(final String contentType) {
        return contentType != null
            && (contentType.startsWith("text/")
                || ADDITIONAL_TEXT_CONTENT_TYPES.contains(contentType));
    }
}
