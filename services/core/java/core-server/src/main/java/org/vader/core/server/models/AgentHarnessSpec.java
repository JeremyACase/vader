package org.vader.core.server.models;

/**
 * Specification for a single agent-harness Job: exactly one task, dispatched under exactly one
 * assignment.
 *
 * @param taskId the id of the {@code TaskEntity} this harness will work
 * @param assignmentId the id of the {@code TaskAttemptEntity} this harness authenticates as --
 *     the harness's control-plane and inference-gateway calls carry this id as their bearer
 *     credential, so it must be unguessable and single-use (minted fresh per attempt)
 */
public record AgentHarnessSpec(String taskId, String assignmentId) {
}
