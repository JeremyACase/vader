package org.vader.core.server.taskagent.model;

/**
 * How much of the model's context one inference turn used.
 *
 * <p>{@code promptTokens} is what the provider reports, and Ollama may leave out the part of a
 * prompt it already held in its cache, so on its own it can understate the prompt's real size.
 * {@code promptCharacters} is measured on what was actually sent -- every message plus every tool
 * definition -- and never understates; together they show how close a turn came to the context
 * window, past which Ollama silently drops the oldest messages.</p>
 *
 * @param promptTokens prompt tokens the provider reports evaluating; {@code 0} when it reports
 *     none
 * @param completionTokens tokens the model generated; {@code 0} when the provider reports none
 * @param promptCharacters characters sent: message contents, tool-call names and arguments, and
 *     tool definitions
 */
public record TurnUsage(long promptTokens, long completionTokens, long promptCharacters) {
}
