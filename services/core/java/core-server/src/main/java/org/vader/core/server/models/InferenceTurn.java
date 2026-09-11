package org.vader.core.server.models;

/**
 * One completed model turn: the text it produced and how many tokens that cost.
 *
 * @param content the model's response text
 * @param tokensSpent tokens consumed by this turn; an estimate where the provider does not
 *     report real usage
 */
public record InferenceTurn(String content, long tokensSpent) {
}
