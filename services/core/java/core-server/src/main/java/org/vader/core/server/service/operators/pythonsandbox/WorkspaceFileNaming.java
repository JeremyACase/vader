package org.vader.core.server.service.operators.pythonsandbox;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.vader.common.model.vader.entity.ObjectMetadataEntity;
import org.vader.core.server.models.WorkspaceFile;

/**
 * Assigns each file attached to a client prompt the name it is staged under in a task attempt's
 * sandbox workspace.
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
 * before its extension when another attached file already claimed it.</p>
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
        var taken = new HashSet<String>();
        return files.stream()
            .sorted(Comparator.comparing(
                ObjectMetadataEntity::getId, Comparator.nullsLast(Comparator.naturalOrder())))
            .map(file -> new WorkspaceFile(
                file.getId(), claim(baseName(file), taken), file.getContentType()))
            .toList();
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
