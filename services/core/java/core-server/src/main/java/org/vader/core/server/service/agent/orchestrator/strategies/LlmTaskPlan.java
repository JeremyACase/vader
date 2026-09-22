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
     * <p>{@code dependsOnIndices} is deliberately index-based rather than referencing other
     * tasks by title or an invented key: a small integer position is far less for a small local
     * model to get wrong than matching free-text strings back up exactly. Indices are
     * additionally constrained to reference only <em>earlier</em> positions in {@code tasks} --
     * enforced by {@code LocalLlmOrchestrationStrategy}, which rejects any plan violating it --
     * so the resulting dependency graph is acyclic by construction; no cycle detection is needed
     * anywhere downstream.</p>
     *
     * @param title a short imperative title
     * @param description what to do, in one or two sentences
     * @param dependsOnIndices the 0-based positions, in {@code tasks}, of every task this one
     *     cannot start until the model produced results for -- empty when nothing must finish
     *     first
     */
    public record LlmTask(
        @JsonPropertyDescription("A short imperative title")
        String title,

        @JsonPropertyDescription("What to do, in one or two sentences")
        String description,

        @JsonPropertyDescription(
            "0-based indices, into this same tasks array, of every task that must complete "
                + "before this one can start. Every index must be strictly less than this "
                + "task's own position -- only reference tasks listed earlier. Leave empty if "
                + "this task can start immediately, in parallel with everything else.")
        List<Integer> dependsOnIndices) {
    }
}
