package org.vader.core.server.service.agent.orchestrator;

/**
 * The result of {@link TaskPlanStructuralValidator#validate}.
 *
 * @param valid {@code true} if the plan is structurally sound
 * @param problem a human-readable (and LLM-actionable) description of what's wrong, or
 *     {@code null} if {@code valid} is {@code true}
 */
public record TaskPlanValidationResult(boolean valid, String problem) {
}
