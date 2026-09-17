package org.vader.core.server.models;

/**
 * Response of {@code POST /vader/core-server/agent/tool-calls}: the tool's raw result, ready for
 * the harness to fold back into the conversation as a {@code TOOL} {@link ConversationMessage}.
 *
 * @param toolCallId echoes the request, so the harness can correlate without tracking order
 * @param resultJson the tool's raw output
 */
public record ToolCallInvocationResult(String toolCallId, String resultJson) {
}
