package org.vader.core.server.sandbox;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.vader.core.server.sandbox.model.SandboxExecutionResult;
import org.vader.core.server.taskagent.TaskAttemptToolContext;

/**
 * The task agent's only Python tool: run code in the calling attempt's own sandbox.
 *
 * <p>Takes no sandbox name: the sandbox comes from the server-supplied {@link ToolContext}
 * ({@link TaskAttemptToolContext}), because a model asked to carry a server-generated name between
 * calls invents one instead. Provisioning, staging attached files and cleanup belong to
 * {@link TaskAttemptSandboxService}. The ad-hoc tools in {@link PythonSandboxTools} are for
 * ops/MCP use only.</p>
 *
 * <p>Takes only {@code code}: extra optional parameters make local models' tool calls fail to
 * parse and invite misuse (such as overwriting an attached file). A model that needs a helper
 * file writes it from Python.</p>
 */
@Component
@ConditionalOnProperty(
    name = {"vader.operators.enabled", "vader.operators.python-sandbox.enabled"},
    havingValue = "true",
    matchIfMissing = false)
public class TaskAttemptSandboxTools {

    @Autowired
    private TaskAttemptSandboxService service;

    /**
     * Runs code in the calling attempt's own sandbox.
     *
     * @param code the Python source to run
     * @param toolContext the server-supplied context identifying the calling attempt
     * @return the run's stdout, stderr, exit code, and whether it timed out
     */
    @Tool(
        name = "run_python_code",
        description = "Run Python code in your own Python sandbox and return its stdout/stderr/"
            + "exit code. Any files attached to the original request are already in the "
            + "working directory -- open them by the filename your task context gives. The "
            + "working directory persists across calls for the rest of this task, so a file you "
            + "write in one call is still there for the next. You see only what the code prints, "
            + "plus the value of a bare expression on its last line (shown as a notebook would), "
            + "so print anything else you want to see.")
    public SandboxExecutionResult runPythonCode(
        @ToolParam(description = "The Python source to run.")
        final String code,
        final ToolContext toolContext) {
        var taskAttemptId = TaskAttemptToolContext.taskAttemptIdFrom(toolContext);
        return this.service.runCode(taskAttemptId, code);
    }
}
