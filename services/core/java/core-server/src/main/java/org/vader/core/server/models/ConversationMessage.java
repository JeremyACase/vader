package org.vader.core.server.models;

import java.util.List;

/**
 * One message in the running conversation a harness sends to {@code /vader/core-server/agent/
 * inference}. Which fields are populated depends on {@code role}:
 * <ul>
 *   <li>{@code SYSTEM} / {@code USER}: {@code content} only.</li>
 *   <li>{@code ASSISTANT}: {@code content} (may be {@code null} when the turn is pure tool calls)
 *       and {@code toolCalls} (the tool calls that turn requested, if any).</li>
 *   <li>{@code TOOL}: {@code toolCallId} and {@code toolName} (correlating to the {@code
 *       ASSISTANT} message's request) and {@code content} (the tool's result).</li>
 * </ul>
 *
 * @param role which participant produced this message
 * @param content the message text (nullable for a pure tool-call {@code ASSISTANT} message)
 * @param toolCalls the tool calls this message requests, if any (empty otherwise)
 * @param toolCallId which prior tool call this message reports the result of ({@code TOOL} only)
 * @param toolName the tool that was called ({@code TOOL} only)
 */
public record ConversationMessage(
    ConversationRole role,
    String content,
    List<InferenceToolCall> toolCalls,
    String toolCallId,
    String toolName) {
}
