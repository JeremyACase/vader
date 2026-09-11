package org.vader.core.server.models;

/**
 * Body of {@code POST /vader/core-server/agent/assignments/{assignmentId}/heartbeat}.
 *
 * @param turnsUsed how many turns this attempt has taken so far
 * @param tokensUsed how many tokens this attempt has spent so far
 */
public record HeartbeatRequest(int turnsUsed, long tokensUsed) {
}
