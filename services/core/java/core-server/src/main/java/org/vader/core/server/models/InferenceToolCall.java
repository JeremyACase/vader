package org.vader.core.server.models;

/**
 * One tool call the model requested (as part of an {@code ASSISTANT} {@link ConversationMessage})
 * before it can produce a final answer.
 *
 * @param id the provider-assigned id correlating this request to its eventual result
 * @param name the tool name, e.g. {@code get_object_content}
 * @param argumentsJson the tool's arguments, as a JSON object string
 */
public record InferenceToolCall(String id, String name, String argumentsJson) {
}
