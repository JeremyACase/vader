package org.vader.core.server.models;

/**
 * Body of {@code POST /vader/core-server/agent/tool-calls}: a harness asking core-server to
 * actually execute one tool call a model requested during an {@code /agent/inference} turn. There
 * is no per-assignment route here either, for the same reason as {@code InferenceRequest}: the
 * assignment id travels in the body.
 *
 * @param assignmentId the calling harness's assignment id
 * @param toolCallId the id correlating this invocation back to the model's request
 * @param toolName the tool to invoke, e.g. {@code get_object_content}
 * @param argumentsJson the tool's arguments, as a JSON object string
 */
public record ToolCallInvocationRequest(
    String assignmentId, String toolCallId, String toolName, String argumentsJson) {
}
