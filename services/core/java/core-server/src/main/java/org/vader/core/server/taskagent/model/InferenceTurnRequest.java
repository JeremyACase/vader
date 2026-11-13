package org.vader.core.server.taskagent.model;

import java.util.List;

/**
 * One inference turn queued for the model: the running conversation, plus which attempt at its
 * task it belongs to, so a retry can sample differently from the attempt before it.
 *
 * @param messages the running conversation so far
 * @param attemptNumber the calling attempt's number, starting at 1 ({@code null} if unknown)
 */
public record InferenceTurnRequest(List<ConversationMessage> messages, Integer attemptNumber) {
}
