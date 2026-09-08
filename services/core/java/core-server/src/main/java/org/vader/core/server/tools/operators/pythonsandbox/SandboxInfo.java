package org.vader.core.server.tools.operators.pythonsandbox;

/**
 * Details of a Python sandbox, returned to REST callers and MCP clients.
 *
 * @param name the sandbox name, shared by its Deployment and Service
 * @param namespace the namespace the sandbox runs in
 * @param phase a coarse lifecycle phase, e.g. {@code "Pending"} or {@code "Running"}
 * @param clusterAddress the in-cluster DNS name other pods can reach the sandbox on
 */
public record SandboxInfo(String name, String namespace, String phase, String clusterAddress) {
}
