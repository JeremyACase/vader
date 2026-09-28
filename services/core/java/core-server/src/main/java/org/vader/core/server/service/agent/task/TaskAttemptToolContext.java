package org.vader.core.server.service.agent.task;

import java.util.Map;
import org.springframework.ai.chat.model.ToolContext;

/**
 * The server-supplied {@link ToolContext} {@link TaskAgentService#invokeTool} hands every tool it
 * invokes on a task agent's behalf, carrying the calling attempt's own id.
 *
 * <p>A tool that needs to know which attempt is calling it declares a {@link ToolContext}
 * parameter and reads the id from here, rather than taking it as an ordinary tool argument:
 * Spring AI leaves {@link ToolContext} parameters out of the JSON schema a model is shown, so the
 * model never sees an id it could get wrong, hallucinate, or aim at another attempt.</p>
 */
public final class TaskAttemptToolContext {

    /** The context key carrying the calling task attempt's id. */
    public static final String TASK_ATTEMPT_ID_KEY = "taskAttemptId";

    private TaskAttemptToolContext() {
    }

    /**
     * Builds the context for a tool call made on behalf of the given attempt.
     *
     * @param taskAttemptId the calling attempt's id
     * @return the tool context
     */
    public static ToolContext of(final String taskAttemptId) {
        return new ToolContext(Map.of(TASK_ATTEMPT_ID_KEY, taskAttemptId));
    }

    /**
     * Reads the calling attempt's id back out of a tool context.
     *
     * @param toolContext the context the tool was invoked with; may be {@code null}
     * @return the attempt id
     * @throws IllegalStateException if the call did not come from a task agent -- e.g. an external
     *     MCP client, whose context carries no attempt id
     */
    public static String taskAttemptIdFrom(final ToolContext toolContext) {
        var value = toolContext == null
            ? null
            : toolContext.getContext().get(TASK_ATTEMPT_ID_KEY);
        if (!(value instanceof String taskAttemptId)) {
            throw new IllegalStateException(
                "This tool only runs on behalf of a task agent's own attempt; no calling attempt "
                    + "was supplied.");
        }
        return taskAttemptId;
    }
}
