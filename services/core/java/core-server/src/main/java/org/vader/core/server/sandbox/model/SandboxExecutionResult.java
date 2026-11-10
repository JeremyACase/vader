package org.vader.core.server.sandbox.model;

import java.util.Objects;

/**
 * The outcome of running one {@link SandboxExecutionRequest} inside a Python sandbox.
 *
 * <p>{@code sessionReset} is boxed so a sandbox image that predates it, and omits it, still
 * deserializes: absent means no reset, since such a sandbox has no session to lose.</p>
 *
 * @param stdout everything the code printed
 * @param stderr everything the code wrote to standard error, including a traceback on failure
 * @param exitCode the process's exit code; {@code -1} when {@code timedOut} is {@code true}
 * @param timedOut whether the run was stopped for exceeding the sandbox's execution timeout
 * @param sessionReset whether the sandbox's Python session was lost during or before this run,
 *     so variables, imports and functions from earlier runs are gone (workspace files are not)
 */
public record SandboxExecutionResult(
    String stdout, String stderr, int exitCode, boolean timedOut, Boolean sessionReset) {

    /** Reads an absent {@code sessionReset} as {@code false}. */
    public SandboxExecutionResult {
        sessionReset = Objects.requireNonNullElse(sessionReset, Boolean.FALSE);
    }
}
