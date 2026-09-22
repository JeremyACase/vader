package org.vader.core.server.service.llm;

import org.vader.core.server.models.ReattemptDecision;

/**
 * The result of one reattempt-decision attempt -- deliberately not an exception even when the LLM
 * was unreachable, for the same reason {@link DecompositionOutcome} isn't: connectivity failure is
 * an expected, everyday outcome the calling strategy makes its own policy decision about, not a
 * bug in the request/response plumbing.
 *
 * @param decision the orchestrator's decision, or {@code null} if {@code unreachableReason} is set
 * @param unreachableReason why the LLM could not be reached, or {@code null} on success
 */
public record ReattemptDecisionOutcome(ReattemptDecision decision, String unreachableReason) {

    /**
     * Whether this outcome represents a connectivity failure rather than a decision.
     *
     * @return {@code true} if the LLM could not be reached
     */
    public boolean isUnreachable() {
        return this.unreachableReason != null;
    }
}
