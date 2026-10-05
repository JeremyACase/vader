package org.vader.core.server.workflow.model;

/**
 * One task of a workflow's plan, as an agent working on or judging one of its tasks sees it:
 * enough to tell which share of the user's request each task owns, and where the task in
 * question sits among them.
 *
 * @param title the task's title
 * @param description the task's description
 * @param depth {@code 0} for a planner task, {@code 1} for a subtask spawned under one, and so on
 * @param underReview whether this is the task whose attempt is being evaluated
 */
public record PlanStep(String title, String description, int depth, boolean underReview) {

    /**
     * This step as one line of an indented outline.
     *
     * @param marker appended when this is the task under review, naming it for the reader
     * @return the line, indented two spaces per level of depth
     */
    public String outlineLine(final String marker) {
        var suffix = this.underReview ? "   <-- " + marker : "";
        return "  ".repeat(this.depth) + "- " + this.title + ": " + this.description + suffix;
    }
}
