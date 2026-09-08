package org.vader.core.server.tools.operators.pythonsandbox;

/**
 * Specification for a single Python sandbox.
 *
 * <p>By the time a spec reaches the operator its {@code name} is already resolved to a valid,
 * unique DNS-1123 label (see {@code SandboxNaming}); the operator does not generate names.</p>
 *
 * @param name the fully resolved sandbox name
 * @param ttlSeconds reserved for a future idle reaper; ignored today
 */
public record PythonSandboxSpec(String name, Integer ttlSeconds) {
}
