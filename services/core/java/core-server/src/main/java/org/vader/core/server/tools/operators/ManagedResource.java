package org.vader.core.server.tools.operators;

import java.util.Map;

/**
 * Immutable snapshot of a unit of infrastructure managed by an operator.
 *
 * <p>A managed resource is identified by a single {@code name} that is shared by every Kubernetes
 * object the operator created for it (its Deployment and its Service).</p>
 *
 * @param name the managed resource name
 * @param namespace the namespace the resource lives in
 * @param phase a coarse lifecycle phase, e.g. {@code "Pending"} or {@code "Running"}
 * @param labels the labels present on the managed Deployment
 */
public record ManagedResource(
    String name,
    String namespace,
    String phase,
    Map<String, String> labels) {
}
