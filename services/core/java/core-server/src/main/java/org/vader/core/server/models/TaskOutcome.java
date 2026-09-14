package org.vader.core.server.models;

/**
 * One task's contribution to a workflow's final synthesized result: what it was asked to do and
 * what its latest attempt actually produced (its result on success, its failure reason
 * otherwise).
 *
 * @param title the task's title
 * @param succeeded whether the task's latest attempt succeeded
 * @param output the attempt's result if it succeeded, its failure reason otherwise
 */
public record TaskOutcome(String title, boolean succeeded, String output) {
}
