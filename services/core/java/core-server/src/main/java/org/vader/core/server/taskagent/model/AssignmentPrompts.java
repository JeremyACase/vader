package org.vader.core.server.taskagent.model;

/**
 * Every piece of text a harness sends the model, rendered by core-server so that all of Vader's
 * prompts live, and are versioned, in one place.
 *
 * @param system the system message that frames the action-loop protocol
 * @param task the opening user message: the task's background, then the task itself
 * @param nudges what to send back when a reply cannot be accepted as the final answer
 */
public record AssignmentPrompts(String system, String task, AssignmentNudges nudges) {
}
