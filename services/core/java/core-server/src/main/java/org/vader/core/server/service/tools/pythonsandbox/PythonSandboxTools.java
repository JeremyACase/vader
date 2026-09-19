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
import org.vader.core.server.models.StagedObjectInfo;
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
     *     workspace before running -- for content the model itself generates, not for
     *     previously-uploaded objects (use {@code stage_object} for those instead) -- or
     *     {@code null} if nothing needs staging
     * @return the run's stdout, stderr, exit code, and whether it timed out
     */
    @Tool(
        name = "run_python_code",
        description = "Run Python code inside an existing sandbox and return its stdout/stderr/"
            + "exit code. The sandbox's working directory persists across calls, so a file "
            + "staged in one call (or a previous run_python_code call, or stage_object) is "
            + "still there for a later one -- stage a file once, then run several analysis "
            + "snippets against it. To analyze a previously-uploaded object, stage it first with "
            + "stage_object and open it here by filename -- do not fetch it with "
            + "get_object_content and pass it via 'files', which would needlessly inline the "
            + "raw bytes into this conversation. 'files' here is only for content you generate "
            + "yourself (e.g. a small helper data file).")
    public SandboxExecutionResult runPythonCode(
        @ToolParam(description = "The exact sandbox name, as returned by create_sandbox or "
            + "list_sandboxes.")
        final String name,
        @ToolParam(description = "The Python source to run.")
        final String code,
        @ToolParam(
            required = false,
            description = "Optional map of filename to base64-encoded content, for content you "
                + "generate yourself, to write into the sandbox's workspace before running the "
                + "code. Do not use this for previously-uploaded objects -- use stage_object.")
        final Map<String, String> files) {
        var request = new SandboxExecutionRequest(code, files, null);
        return this.service.runCode(name, request);
    }

    /**
     * Stages a previously-uploaded object directly into a sandbox's workspace.
     *
     * @param sandboxName the exact sandbox name, as returned by create or list
     * @param objectMetadataId the {@code ObjectMetadata} id, from {@code query_object_metadata}
     *     or {@code get_object_metadata_by_id}
     * @param filename the name to stage it under; the object's original filename when omitted
     * @return the staged filename, content type, and size -- never the content itself
     */
    @Tool(
        name = "stage_object",
        description = "Fetch a previously-uploaded object directly into an existing sandbox's "
            + "persistent workspace, without its content ever passing through this "
            + "conversation. Use this -- not get_object_content plus run_python_code's 'files' "
            + "-- for any file you intend to analyze with code, especially spreadsheets, "
            + "images, or any other binary format; it also has no size limit tied to a model's "
            + "context. Returns only the staged filename, content type, and size. "
            + "run_python_code can then open it by that filename from the sandbox's working "
            + "directory.")
    public StagedObjectInfo stageObject(
        @ToolParam(description = "The exact sandbox name, as returned by create_sandbox or "
            + "list_sandboxes.")
        final String sandboxName,
        @ToolParam(description = "The ObjectMetadata id, from query_object_metadata or "
            + "get_object_metadata_by_id.")
        final String objectMetadataId,
        @ToolParam(
            required = false,
            description = "Optional filename to stage the object as inside the sandbox's "
                + "workspace; defaults to the object's original filename when omitted.")
        final String filename) {
        return this.service.stageObject(sandboxName, objectMetadataId, filename);
    }
}
