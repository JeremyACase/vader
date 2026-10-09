package org.vader.core.server.sandbox;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.vader.core.server.sandbox.model.SandboxExecutionResult;
import org.vader.core.server.storage.ObjectDescriptor;
import org.vader.core.server.taskagent.TaskAttemptToolContext;

/**
 * The task agent's sandbox tools: run code in the calling attempt's own sandbox, and upload a
 * file that code wrote to object storage.
 *
 * <p>Neither takes a sandbox name: the sandbox comes from the server-supplied {@link ToolContext}
 * ({@link TaskAttemptToolContext}), because a model asked to carry a server-generated name between
 * calls invents one instead. Provisioning, staging attached files and cleanup belong to
 * {@link TaskAttemptSandboxService}. The ad-hoc tools in {@link PythonSandboxTools} are for
 * ops/MCP use only.</p>
 *
 * <p>Each takes a single parameter: extra optional parameters make local models' tool calls fail
 * to parse and invite misuse (such as overwriting an attached file). A model that needs a helper
 * file writes it from Python; an upload's content type is inferred from its filename.</p>
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
            + "so print anything else you want to see. The sandbox has no network access: use "
            + "the standard library, pandas or openpyxl; nothing else can be installed.")
    public SandboxExecutionResult runPythonCode(
        @ToolParam(description = "The Python source to run.")
        final String code,
        final ToolContext toolContext) {
        var taskAttemptId = TaskAttemptToolContext.taskAttemptIdFrom(toolContext);
        return this.service.runCode(taskAttemptId, code);
    }

    /**
     * Uploads a file from the calling attempt's sandbox workspace to object storage.
     *
     * @param filename the file's path relative to the working directory
     * @param toolContext the server-supplied context identifying the calling attempt
     * @return the stored object's id, filename, content type, and size
     */
    @Tool(
        name = "upload_object",
        description = "Save a file from your Python working directory to permanent object "
            + "storage, so it outlives this task and the user can download it -- e.g. a report "
            + "you wrote or a spreadsheet you edited. Write the file with run_python_code "
            + "first, then pass its filename here; its content never passes through this "
            + "conversation, so any size or format works. Returns the stored object's id; "
            + "mention it and the filename in your final result.")
    public ObjectDescriptor uploadObject(
        @ToolParam(description = "The file's name (or path) in your Python working directory, "
            + "e.g. \"report.md\".")
        final String filename,
        final ToolContext toolContext) {
        var taskAttemptId = TaskAttemptToolContext.taskAttemptIdFrom(toolContext);
        return this.service.uploadFile(taskAttemptId, filename);
    }
}
