package org.vader.core.server.service.registries;

/**
 * Which kind of internally-spawned agent a tool is offered to. Scopes what
 * {@link McpToolCallbackRegistry#forAudience} hands to a model -- it has no bearing on what an
 * external MCP client connecting to the SSE endpoint sees, since Spring AI's MCP server
 * auto-configuration discovers every {@code ToolCallbackProvider} bean directly, independent of
 * this registry.
 */
public enum AgentToolAudience {

    /**
     * A per-task {@code core-agent-harness} run, driven by
     * {@code InterfaceInferenceGatewayStrategy}. Gets tools for actually carrying out one
     * task-graph subtask (sandbox execution, object retrieval) -- never tools that reach outside
     * its own task.
     */
    TASK_EXECUTION,

    /**
     * A higher-level agent reasoning about the system as a whole -- today, prompt decomposition
     * ({@code LocalLlmOrchestrationStrategy}); later, a supervisor capable of things like
     * alleviating back pressure by provisioning more resources. Never gets sandbox code execution.
     */
    ORCHESTRATION,
}
