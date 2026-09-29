package org.vader.core.server.taskagent;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs one registered tool's {@link ToolCallback#call} in its own, independent transaction --
 * {@link Propagation#REQUIRES_NEW} -- rather than joining whatever transaction the caller is
 * already inside.
 *
 * <p>Several tools (e.g. the DAO query tools) are themselves {@code @Transactional}. Joined to
 * the caller's transaction, a failing one would mark it rollback-only, so the caller's later
 * commit -- including the tool-call audit row -- would fail with
 * {@code UnexpectedRollbackException} even after it caught the error. In its own transaction, a
 * failure rolls back only the tool's work.</p>
 */
@Component
public class ToolCallInvocationBoundary {

    /**
     * Invokes the given tool with the given arguments in a new, independent transaction.
     *
     * @param toolCallback the tool to invoke
     * @param argumentsJson the tool's arguments, as a JSON object string
     * @param toolContext server-supplied context the model never sees (see
     *     {@link TaskAttemptToolContext}); a tool that doesn't declare a {@link ToolContext}
     *     parameter simply ignores it
     * @return the tool's raw result
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String invoke(
            final ToolCallback toolCallback, final String argumentsJson,
            final ToolContext toolContext) {
        return toolCallback.call(argumentsJson, toolContext);
    }
}
