package org.vader.core.server.models;

/**
 * The orchestrator's decision on whether a failed task is worth re-attempting. Doubles as the
 * structured-output shape a local LLM is asked to produce.
 *
 * @param shouldReattempt {@code true} if a fresh attempt should be dispatched
 * @param reasoning why -- persisted as the resulting {@code TaskUpdate}'s description
 */
public record ReattemptDecision(boolean shouldReattempt, String reasoning) {
}
