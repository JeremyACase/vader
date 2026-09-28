package org.vader.core.server.models;

import java.util.List;

/**
 * Body of {@code POST /vader/core-server/agent/inference}: the only path a harness has to any
 * LLM. {@code assignmentId} attributes the call to a specific, still-open attempt so it can be
 * logged and rejected once that attempt is no longer running.
 *
 * @param assignmentId the calling harness's assignment id
 * @param messages the running conversation so far, including any of the harness's own tool-call
 *     results from a prior turn
 */
public record InferenceRequest(String assignmentId, List<ConversationMessage> messages) {
}
