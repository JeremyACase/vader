package org.vader.core.server.service.agent.orchestrator.strategies;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * The minimal shape the orchestrator LLM is asked to produce: only the fields a
 * {@link org.vader.common.model.vader.dto.TaskPlan} actually needs from the model, with none of
 * the identity or audit fields it inherits from {@code AbstractModel}.
 *
 * <p>Keeping the generated JSON schema this small is what lets smaller local models return valid
 * output — asking them to fill an {@code id} that must match a UUID pattern, plus timestamps and
 * a recursive sub-task tree, reliably produced nulls.</p>
 *
 * <p>{@code reasoning} is the first field so the model writes its chain-of-thought before
 * committing to the structured plan fields.</p>
 */
@JsonClassDescription("A problem decomposed into a short list of actionable tasks")
public record LlmTaskPlan(
    @JsonPropertyDescription(
        "Step-by-step reasoning before the plan: restate the goal in your own words, identify "
            + "constraints or unknowns, decide whether any available tools would help, then "
            + "sketch your approach. Write this before filling in objective and tasks.")
    String reasoning,

    @JsonPropertyDescription("One sentence restating the user's goal")
    String objective,

    @JsonPropertyDescription(
        "2 to 6 top-level tasks that together accomplish the objective, listed in the order "
            + "they would naturally happen")
    List<LlmTask> tasks) {

    /**
     * One task within an {@link LlmTaskPlan}.
     *
     * <p>{@code dependsOn} names earlier tasks by <em>title</em>. It was once 0-based indices,
     * on the theory that a small integer is less for a small model to get wrong -- in practice
     * qwen2.5:3b left every index list empty, even for an obviously sequential plan and even
     * when told exactly which dependency was missing. Repeating a title it just wrote is a far
     * easier ask. References must still name tasks listed <em>earlier</em> -- enforced by
     * {@code LocalLlmOrchestrationStrategy}, which rejects any plan violating it -- so the
     * resulting graph is acyclic by construction.</p>
     *
     * @param title a short imperative title
     * @param description what to do, in one or two sentences
     * @param dependsOn the titles of every earlier task this one cannot start until it finishes
     *     -- empty when nothing must finish first
     */
    public record LlmTask(
        @JsonPropertyDescription("A short imperative title, unique within this plan")
        String title,

        @JsonPropertyDescription("What to do, in one or two sentences")
        String description,

        @JsonPropertyDescription(
            "The exact titles of the tasks listed earlier in this plan that must finish before "
                + "this one can start -- e.g. a task that analyzes data depends on the task "
                + "that reads it. Only name tasks listed before this one. Leave empty only if "
                + "this task truly needs nothing from any other task.")
        List<String> dependsOn) {
    }
}
