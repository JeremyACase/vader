package org.vader.core.server.service.operators.pythonsandbox;

/**
 * Turns a task attempt id into the name of the one sandbox that attempt owns.
 *
 * <p>Deterministic by design: every replica, and every call within the attempt, resolves the same
 * name without storing it anywhere -- which is what lets the sandbox be provisioned lazily on the
 * first code run and deleted on settlement without either step needing to look the other up. An
 * attempt id is already a UUID (lowercase hex and hyphens), so the result is a valid DNS-1123
 * label as-is: the 22-character prefix plus a 36-character UUID stays under the 63-character
 * limit.</p>
 */
public final class TaskAttemptSandboxNaming {

    /** Prefix shared by every attempt-owned sandbox; distinct from ad-hoc sandbox names. */
    public static final String PREFIX = SandboxNaming.PREFIX + "attempt-";

    private TaskAttemptSandboxNaming() {
    }

    /**
     * Resolves the sandbox name for the given task attempt id.
     *
     * @param taskAttemptId the task attempt (assignment) id
     * @return the sandbox name
     */
    public static String resolve(final String taskAttemptId) {
        return PREFIX + taskAttemptId;
    }
}
