package org.vader.core.server.orchestration;

import java.util.Locale;

/**
 * Decides whether a task title an LLM wrote down as a dependency reference names a given task.
 *
 * <p>Tolerates only what a model plausibly gets wrong repeating a title it just wrote -- case,
 * surrounding whitespace or quotes, doubled spaces -- so a reference can never silently land on a
 * different task.</p>
 */
public final class TaskTitleMatcher {

    private TaskTitleMatcher() {
    }

    /**
     * Whether {@code reference} names the task titled {@code title}.
     *
     * @param title a task's actual title
     * @param reference a title as the model repeated it; may be {@code null}
     * @return {@code true} if they name the same task
     */
    public static boolean matches(final String title, final String reference) {
        return title != null && reference != null
            && normalize(title).equals(normalize(reference));
    }

    private static String normalize(final String text) {
        return text.strip()
            .replaceAll("^[\"'`]+|[\"'`]+$", "")
            .replaceAll("\\s+", " ")
            .strip()
            .toLowerCase(Locale.ROOT);
    }
}
