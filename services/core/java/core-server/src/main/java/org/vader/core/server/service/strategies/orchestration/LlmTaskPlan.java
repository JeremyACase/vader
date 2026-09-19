package org.vader.core.server.service.strategies.orchestration;

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

    @JsonPropertyDescription("2 to 6 top-level tasks that together accomplish the objective")
    List<LlmTask> tasks) {

    /**
     * One task within an {@link LlmTaskPlan}.
     *
     * @param title a short imperative title
     * @param description what to do, in one or two sentences
     */
    public record LlmTask(
        @JsonPropertyDescription("A short imperative title")
        String title,

        @JsonPropertyDescription("What to do, in one or two sentences")
        String description) {
    }
}
