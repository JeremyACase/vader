package org.vader.core.server.review.model;

/**
 * One concrete, self-contained step an evaluator judged still left to do on an attempt that made
 * real but unfinished progress. Becomes a runtime subtask of the attempt's task.
 *
 * @param title a short title for the step
 * @param description what the step must accomplish, written so an agent that never saw the
 *     original attempt can carry it out
 */
public record RemainingSubtask(String title, String description) {
}
