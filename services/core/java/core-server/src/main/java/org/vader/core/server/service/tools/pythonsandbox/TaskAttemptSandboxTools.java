package org.vader.core.server.service.tools.pythonsandbox;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.vader.core.server.models.SandboxExecutionResult;
import org.vader.core.server.service.agent.task.TaskAttemptToolContext;
import org.vader.core.server.service.operators.pythonsandbox.TaskAttemptSandboxService;

/**
 * The task agent's only Python tool: run code in the calling attempt's own sandbox.
 *
 * <p>Deliberately takes no sandbox name. Which sandbox to use comes from the server-supplied
 * {@link ToolContext} ({@link TaskAttemptToolContext}), never from the model -- a model asked to
 * carry a server-generated name from one tool call into the next will, in practice, invent one
 * instead. Provisioning, staging the request's attached files, and cleanup are all
 * {@link TaskAttemptSandboxService}'s job. The ad-hoc sandbox tools in {@link PythonSandboxTools}
 * remain for ops/MCP use, but are not offered to task agents.</p>
 *
 * <p>Deliberately takes only {@code code}, too. It used to also accept an optional map of
 * filename to base64 content to write before running, and with that parameter in the schema
 * Ollama silently discarded {@code qwen2.5}'s otherwise well-formed tool calls -- the model got
 * back neither text nor a tool call, every turn. The model also misused it, passing an attached
 * file's name with empty content, which would have overwritten the attachment. A model that needs
 * a helper file can write one from its own Python code.</p>
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
