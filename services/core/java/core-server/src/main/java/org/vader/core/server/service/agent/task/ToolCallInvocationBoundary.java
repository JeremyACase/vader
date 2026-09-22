package org.vader.core.server.service.agent.task;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs one registered tool's {@link ToolCallback#call} in its own, independent transaction --
 * {@link Propagation#REQUIRES_NEW} -- rather than joining whatever transaction the caller is
 * already inside.
 *
 * <p>Several registered tools (e.g. the DAO query tools from
 * {@code GenericVaderDaoController}) are themselves {@code @Transactional}. Without this
 * boundary, such a tool runs as a nested participant in {@code TaskAgentService.invokeTool}'s
 * own transaction; if it throws, Spring marks that shared transaction rollback-only before the
 * exception ever reaches {@code TaskAgentService}'s catch block. The catch then swallows the
 * exception and the surrounding code proceeds as if nothing happened, but the subsequent commit
 * -- which also needs to durably record the tool-call audit row -- fails with
 * {@code UnexpectedRollbackException}, surfacing to the calling harness as a bare 500
 * indistinguishable from a genuine connectivity failure. Giving the tool call its own
 * transaction means a failure inside it rolls back only that transaction, leaving the caller's
 * transaction free to record the (now-caught) failure as an ordinary tool result.</p>
 */
@Component
public class ToolCallInvocationBoundary {

    /**
     * Invokes the given tool with the given arguments in a new, independent transaction.
     *
     * @param toolCallback the tool to invoke
     * @param argumentsJson the tool's arguments, as a JSON object string
     * @return the tool's raw result
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String invoke(final ToolCallback toolCallback, final String argumentsJson) {
        return toolCallback.call(argumentsJson);
    }
}
