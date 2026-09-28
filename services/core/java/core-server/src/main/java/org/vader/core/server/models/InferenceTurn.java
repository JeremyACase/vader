package org.vader.core.server.models;

import java.util.List;

/**
 * One completed model turn: either a final answer ({@code content} set, {@code toolCalls} empty)
 * or a request to call one or more tools before it can continue ({@code toolCalls} populated,
 * {@code content} possibly {@code null}). The harness is responsible for actually invoking any
 * requested tool call (via {@code /vader/core-server/agent/tool-calls}) and folding the result
 * back into the next turn's conversation -- this record only reports what the model asked for.
 *
 * @param content the model's response text, or {@code null} when the turn is pure tool calls
 * @param toolCalls the tool calls the model wants executed before it continues; empty for a final
 *     answer
 * @param tokensSpent tokens consumed by this turn; an estimate where the provider does not report
 *     real usage
 * @param finishReason why the model stopped generating, as the provider reports it (Ollama:
 *     {@code stop} for a natural end, {@code length} when the reply was cut off at the output
 *     token cap); {@code null} when the provider reports none. A {@code length} turn's content or
 *     tool calls are incomplete, however plausible they look
 */
public record InferenceTurn(
    String content, List<InferenceToolCall> toolCalls, long tokensSpent, String finishReason) {
}
