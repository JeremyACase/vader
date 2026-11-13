package org.vader.core.server.taskagent.model;

/**
 * What a harness sends the model in place of accepting a reply as its final answer.
 *
 * @param emptyReply after a turn with neither text nor tool calls
 * @param unexecutedCode after a final answer carrying code the model never ran
 * @param unresolvedError after a final answer that follows a failed tool call, with nothing
 *     succeeding since
 */
public record AssignmentNudges(String emptyReply, String unexecutedCode, String unresolvedError) {
}
