package org.vader.core.server.service.tools.pythonsandbox;

import java.util.List;
import java.util.Map;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.vader.core.server.models.SandboxExecutionRequest;
import org.vader.core.server.models.SandboxExecutionResult;
import org.vader.core.server.models.SandboxInfo;
import org.vader.core.server.service.operators.pythonsandbox.PythonSandboxService;

/**
 * Exposes the Python sandbox operator to LLMs as MCP tools. Each method delegates straight to
 * {@link PythonSandboxService}; thrown exceptions are surfaced to the caller as a tool error by
 * Spring AI.
 */
@Component
@ConditionalOnProperty(
    prefix = "vader.operators.python-sandbox",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false)
public class PythonSandboxTools {

    @Autowired
    private PythonSandboxService service;

    /**
     * Creates a new isolated Python sandbox pod in the cluster.
     *
     * @param name an optional friendly name; a unique name is generated when omitted
     * @return the created (or already-existing) sandbox
     */
    @Tool(
        name = "create_sandbox",
        description = "Create a new isolated Python sandbox pod in the Kubernetes cluster. "
            + "Returns the sandbox name, namespace, lifecycle phase and in-cluster address.")
    public SandboxInfo createSandbox(
        @ToolParam(
            required = false,
            description = "Optional friendly name for the sandbox; a unique name is generated "
                + "when omitted.")
        final String name) {
        return this.service.create(name);
    }

    /**
     * Lists every Python sandbox currently managed by this operator.
     *
     * @return the sandboxes; empty if none
     */
    @Tool(
        name = "list_sandboxes",
        description = "List every Python sandbox currently managed by this operator.")
    public List<SandboxInfo> listSandboxes() {
        return this.service.list();
    }

    /**
     * Deletes a Python sandbox and its Kubernetes resources.
     *
     * @param name the exact sandbox name, as returned by create or list
     * @return a short confirmation message
     */
    @Tool(
        name = "delete_sandbox",
        description = "Delete a Python sandbox and its Kubernetes resources by name.")
    public String deleteSandbox(
        @ToolParam(description = "The exact sandbox name, as returned by create_sandbox or "
            + "list_sandboxes.")
        final String name) {
        this.service.delete(name);
        return "Deleted sandbox '" + name + "'.";
    }

    /**
     * Runs code inside an existing sandbox.
     *
     * @param name the exact sandbox name, as returned by create or list
     * @param code the Python source to run
     * @param files a map of filename to base64-encoded content to stage into the sandbox's
     *     workspace before running -- e.g. the output of {@code get_object_content} -- or
     *     {@code null} if nothing needs staging
     * @return the run's stdout, stderr, exit code, and whether it timed out
     */
    @Tool(
        name = "run_python_code",
        description = "Run Python code inside an existing sandbox and return its stdout/stderr/"
            + "exit code. The sandbox's working directory persists across calls, so a file "
            + "staged in one call (or a previous run_python_code call) is still there for a "
            + "later one -- stage a file once, then run several analysis snippets against it. "
            + "To analyze an uploaded file, fetch its content with get_object_content first and "
            + "pass it here via 'files' (filename -> that same base64 content, unmodified).")
    public SandboxExecutionResult runPythonCode(
        @ToolParam(description = "The exact sandbox name, as returned by create_sandbox or "
            + "list_sandboxes.")
        final String name,
        @ToolParam(description = "The Python source to run.")
        final String code,
        @ToolParam(
            required = false,
            description = "Optional map of filename to base64-encoded content to write into "
                + "the sandbox's workspace before running the code.")
        final Map<String, String> files) {
        var request = new SandboxExecutionRequest(code, files, null);
        return this.service.runCode(name, request);
    }
}
