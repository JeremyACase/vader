package org.vader.core.server.models;

/**
 * An evaluator's independent verdict on one settled attempt. Doubles as the structured-output
 * shape a local LLM is asked to produce -- there is no lean-vs-full distinction to make here,
 * unlike decomposition's {@code LlmTaskPlan}.
 *
 * @param passed {@code true} if the evaluator judges the attempt to have actually succeeded
 * @param reasoning why -- persisted as the resulting {@code TaskUpdate}'s description
 */
public record EvaluationVerdict(boolean passed, String reasoning) {
}
