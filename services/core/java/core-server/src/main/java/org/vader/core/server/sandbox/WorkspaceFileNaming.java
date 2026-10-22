package org.vader.core.server.sandbox;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.vader.common.model.vader.entity.ObjectMetadataEntity;
import org.vader.core.server.sandbox.model.WorkspaceFile;

/**
 * Assigns each file a task attempt's sandbox workspace starts with -- those attached to the client
 * prompt, and those earlier tasks saved -- the name it is staged under.
 *
 * <p>The single source of truth for those names: both the context a task agent is shown ("this
 * file is in your working directory as ...") and the staging itself go through here, so the name
 * a model is told is always the name actually on disk. Deterministic for the same set of files --
 * ordered by id, not by the {@code Set}'s own iteration order -- so every call within an attempt
 * agrees.</p>
 *
 * <p>An original filename is reduced to a single path segment (a separator could otherwise nest
 * it in a subdirectory, or a bare {@code ..} escape the workspace entirely), falls back to the
 * object id when nothing usable is left, and is suffixed {@code " (2)"}, {@code " (3)"}, ...
 * before its extension when another file already claimed it.</p>
 *
 * <p>Attached files claim their names first. Of the files earlier tasks saved, only the newest of
 * each name is kept, since a later save of a file supersedes an earlier one exactly as it would
 * in a shared directory; one that clashes with an attached file is numbered rather than hiding
 * the client's original.</p>
 */
public final class WorkspaceFileNaming {

    private static final Set<String> UNUSABLE_NAMES = Set.of("", ".", "..");

    private WorkspaceFileNaming() {
    }

    /**
     * Assigns a unique workspace filename to every attached file.
     *
     * @param files the files attached to a client prompt
     * @return one entry per file, ordered by object id
     */
    public static List<WorkspaceFile> assign(final Collection<ObjectMetadataEntity> files) {
        return assign(files, List.of());
    }

    /**
     * Assigns a unique workspace filename to every attached file, then to the newest file of each
     * name that earlier tasks saved.
     *
     * @param attachments the files attached to a client prompt
     * @param upstreamNewestFirst the files earlier tasks saved, newest first
     * @return the attached files ordered by object id, then the kept saved files newest first
     */
    public static List<WorkspaceFile> assign(
            final Collection<ObjectMetadataEntity> attachments,
            final List<ObjectMetadataEntity> upstreamNewestFirst) {
        var taken = new HashSet<String>();
        var attached = attachments.stream()
            .sorted(Comparator.comparing(
                ObjectMetadataEntity::getId, Comparator.nullsLast(Comparator.naturalOrder())))
            .map(file -> claimed(file, taken))
            .toList();
        var seen = new HashSet<String>();
        var saved = upstreamNewestFirst.stream()
            .filter(file -> seen.add(baseName(file)))
            .map(file -> claimed(file, taken))
            .toList();
        return Stream.concat(attached.stream(), saved.stream()).toList();
    }

    private static WorkspaceFile claimed(final ObjectMetadataEntity file, final Set<String> taken) {
        return new WorkspaceFile(
            file.getId(), claim(baseName(file), taken), file.getContentType(), producedBy(file));
    }

    private static String producedBy(final ObjectMetadataEntity file) {
        var attempt = file.getTaskAttempt();
        return Objects.isNull(attempt) ? null : attempt.getTask().getTitle();
    }

    private static String baseName(final ObjectMetadataEntity file) {
        var sanitized = sanitize(file.getOriginalFilename());
        return UNUSABLE_NAMES.contains(sanitized) ? file.getId() : sanitized;
    }

    private static String sanitize(final String originalFilename) {
        return originalFilename == null
            ? ""
            : originalFilename.replaceAll("[/\\\\]", "_").strip();
    }

    private static String claim(final String candidate, final Set<String> taken) {
        var name = candidate;
        var copy = 2;
        while (!taken.add(name)) {
            name = numbered(candidate, copy);
            copy++;
        }
        return name;
    }

    private static String numbered(final String name, final int copy) {
        var extensionStart = name.lastIndexOf('.');
        return extensionStart > 0
            ? name.substring(0, extensionStart) + " (" + copy + ")" + name.substring(extensionStart)
            : name + " (" + copy + ")";
    }
}
