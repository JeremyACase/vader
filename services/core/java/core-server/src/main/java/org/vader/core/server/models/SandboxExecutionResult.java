package org.vader.core.server.models;

/**
 * The outcome of running one {@link SandboxExecutionRequest} inside a Python sandbox.
 *
 * @param stdout everything the code printed
 * @param stderr everything the code wrote to standard error, including a traceback on failure
 * @param exitCode the process's exit code; {@code -1} when {@code timedOut} is {@code true}
 * @param timedOut whether the run was killed for exceeding the sandbox's execution timeout
 */
public record SandboxExecutionResult(
    String stdout, String stderr, int exitCode, boolean timedOut) {
}
