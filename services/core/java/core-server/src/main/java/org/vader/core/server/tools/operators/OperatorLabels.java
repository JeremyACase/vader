package org.vader.core.server.tools.operators;

/**
 * Label keys stamped on every Kubernetes object an operator creates.
 *
 * <p>The operator scopes its list and delete operations to these labels, so it never touches a
 * resource it did not create.</p>
 */
public final class OperatorLabels {

    /**
     * Broad marker whose value is always {@code "vader"}; identifies a resource as operator-owned
     * without saying which operator owns it.
     */
    public static final String MANAGED_BY = "operators.vader.org/managed-by";

    /**
     * Value stamped into {@link #MANAGED_BY}.
     */
    public static final String MANAGED_BY_VALUE = "vader";

    /**
     * Label whose value is the name of the operator that owns the resource, e.g.
     * {@code "python-sandbox"}.
     */
    public static final String OPERATOR = "operators.vader.org/operator";

    private OperatorLabels() {
    }
}
