package org.vader.core.server.service.operators.agentharness;

/**
 * Turns an assignment id into the Job name that carries it.
 *
 * <p>An assignment id is already a UUID -- lowercase hex and hyphens -- so it is DNS-1123-label
 * valid as-is; only a stable, greppable prefix needs adding.</p>
 */
public final class AgentHarnessNaming {

    /** Prefix shared by every agent-harness Job. */
    public static final String PREFIX = "agent-harness-";

    private AgentHarnessNaming() {
    }

    /**
     * Resolves the Job name for the given assignment id.
     *
     * @param assignmentId the assignment (attempt) id
     * @return the Job name
     */
    public static String resolve(final String assignmentId) {
        return PREFIX + assignmentId;
    }
}
