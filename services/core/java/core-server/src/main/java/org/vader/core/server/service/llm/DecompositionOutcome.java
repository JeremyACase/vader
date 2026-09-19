package org.vader.core.server.service.llm;

import org.vader.core.server.service.strategies.orchestration.LlmTaskPlan;

/**
 * The result of one decomposition attempt -- deliberately not an exception even when the LLM was
 * unreachable: connectivity failure is an expected, everyday outcome {@code
 * LocalLlmOrchestrationStrategy} makes its own policy decision about (fall back to a static plan,
 * or fail loudly), not a bug in the request/response plumbing. Genuinely unexpected failures
 * (the request/response JSON itself being malformed, say) still propagate as exceptions and settle
 * the underlying queue message {@code FAILED}, same as any other inbox.
 *
 * @param plan the model's plan, or {@code null} if {@code unreachableReason} is set
 * @param unreachableReason why the LLM could not be reached, or {@code null} on success
 */
public record DecompositionOutcome(LlmTaskPlan plan, String unreachableReason) {

    /**
     * Whether this outcome represents a connectivity failure rather than a plan.
     *
     * @return {@code true} if the LLM could not be reached
     */
    public boolean isUnreachable() {
        return this.unreachableReason != null;
    }
}
