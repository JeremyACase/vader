package org.vader.core.server.tools.operators.pythonsandbox.mcp;

import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.vader.core.server.tools.operators.pythonsandbox.PythonSandboxService;
import org.vader.core.server.tools.operators.pythonsandbox.SandboxInfo;

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
}
