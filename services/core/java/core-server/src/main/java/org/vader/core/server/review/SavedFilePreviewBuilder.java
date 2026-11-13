package org.vader.core.server.review;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.vader.core.server.review.model.SavedFilePreview;
import org.vader.core.server.storage.FileStorageException;
import org.vader.core.server.storage.ObjectContentTypeResolver;
import org.vader.core.server.storage.ObjectDescriptor;
import org.vader.core.server.storage.ObjectStorageService;

/**
 * Builds the reviewer's view of the files an attempt saved, oldest first: each file's name, type
 * and size, plus the opening lines of every text file, so a review can check what a file holds
 * against what the request asked of it.
 *
 * <p>A file is text when its content type says so, or when its opening bytes are valid UTF-8 with
 * no NUL byte -- so any text format previews, whatever its extension, and a binary one never
 * does. Only a file's head is read, and previews share a fixed budget so a long list of files
 * cannot crowd the rest of the review out of the model's context; files past the budget are listed
 * without one.</p>
 */
@Component
public class SavedFilePreviewBuilder {

    /** The most lines of one file a preview shows. */
    static final int MAX_LINES = 20;

    /** The most bytes of one file read for its preview. */
    static final int MAX_BYTES_PER_FILE = 2 * 1024;

    /** The most preview characters across all of an attempt's files. */
    static final int MAX_TOTAL_CHARACTERS = 6 * 1024;

    @Autowired
    private ObjectStorageService objectStorageService;

    /**
     * Previews every file an attempt saved.
     *
     * @param taskAttemptId the attempt under review
     * @return one preview per saved file, oldest first, possibly none
     */
    public List<SavedFilePreview> build(final String taskAttemptId) {
        var previews = new ArrayList<SavedFilePreview>();
        var remaining = MAX_TOTAL_CHARACTERS;
        for (var file : this.objectStorageService.describeTaskAttemptOutputs(taskAttemptId)) {
            var preview = this.previewOf(file, Math.min(MAX_BYTES_PER_FILE, remaining));
            remaining -= Objects.requireNonNullElse(preview.preview(), "").length();
            previews.add(preview);
        }
        return previews;
    }

    private SavedFilePreview previewOf(final ObjectDescriptor file, final int byteLimit) {
        return file.size() > 0 && byteLimit > 0
            ? this.contentPreview(file, this.headOf(file.id(), byteLimit))
            : new SavedFilePreview(
                file.filename(), file.contentType(), file.size(), null, false, false);
    }

    private SavedFilePreview contentPreview(final ObjectDescriptor file, final byte[] head) {
        return textOf(head, ObjectContentTypeResolver.isText(file.contentType()))
            .map(text -> textPreview(file, head.length, text))
            .orElseGet(() -> new SavedFilePreview(
                file.filename(), file.contentType(), file.size(), null, false, true));
    }

    private static SavedFilePreview textPreview(
            final ObjectDescriptor file, final int bytesRead, final String text) {
        var lines = text.lines().toList();
        var shown = lines.stream().limit(MAX_LINES).collect(Collectors.joining("\n"));
        var truncated = bytesRead < file.size() || lines.size() > MAX_LINES;
        return new SavedFilePreview(
            file.filename(), file.contentType(), file.size(), shown, truncated, false);
    }

    /**
     * A head that decodes strictly is text whatever its declared type; one declared as text is
     * shown even when it doesn't, with its undecodable bytes replaced.
     */
    private static Optional<String> textOf(final byte[] head, final boolean declaredText) {
        var strict = decoded(head, CodingErrorAction.REPORT)
            .filter(text -> text.indexOf('\0') < 0);
        return declaredText ? strict.or(() -> decoded(head, CodingErrorAction.REPLACE)) : strict;
    }

    private static Optional<String> decoded(final byte[] head, final CodingErrorAction onError) {
        var decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(onError)
            .onUnmappableCharacter(onError);
        var chars = CharBuffer.allocate(head.length);
        // Not end of input: a character the byte limit cut in half is left out, not rejected.
        var result = decoder.decode(ByteBuffer.wrap(head), chars, false);
        return result.isError() ? Optional.empty() : Optional.of(chars.flip().toString());
    }

    private byte[] headOf(final String objectMetadataId, final int byteLimit) {
        var resource = this.objectStorageService.retrieve(objectMetadataId).resource();
        try (InputStream in = resource.getInputStream()) {
            return in.readNBytes(byteLimit);
        } catch (IOException e) {
            throw new FileStorageException("Could not read a saved file to preview it", e);
        }
    }
}
