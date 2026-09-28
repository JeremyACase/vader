package org.vader.core.server.models;

import org.vader.common.model.vader.dto.TaskPlan;

/**
 * Everything an {@code InterfaceTaskPlanRefinementStrategy} needs to critique a freshly-decomposed
 * task plan. Built only once the plan has already passed {@code TaskPlanStructuralValidator} --
 * this request is never even assembled for a structurally-broken plan, since that problem is
 * already known without spending an LLM call to find it.
 *
 * @param originalRequestText the user's original prompt, so the critique can judge whether the
 *     plan actually addresses it, not just whether it is internally consistent
 * @param taskPlan the decomposed plan to critique
 */
public record TaskPlanRefinementRequest(String originalRequestText, TaskPlan taskPlan) {
}
