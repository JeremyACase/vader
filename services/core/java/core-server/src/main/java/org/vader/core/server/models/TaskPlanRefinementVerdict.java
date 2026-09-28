package org.vader.core.server.models;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;
import java.util.Objects;

/**
 * The refinement critique's verdict on a freshly-decomposed task plan, before it is ever
 * persisted. Doubles as the structured-output shape a local LLM is asked to produce.
 *
 * <p>Missing dependencies are reported as data, not prose, and applied to the plan directly --
 * never by sending the whole plan back for re-planning. A small model reliably <em>spots</em> a
 * missing dependency but, asked to re-plan, regenerates the same plan with the edge still missing;
 * so the edge it named is simply added. {@code needsRevision} is reserved for problems only
 * re-planning can fix.</p>
 *
 * @param needsRevision {@code true} if the plan has a problem, other than missing dependencies,
 *     worth asking the orchestrator to re-plan for
 * @param reasoning why -- fed back to the orchestrator as revision guidance when
 *     {@code needsRevision} is {@code true}, or just an explanation of what looked fine otherwise
 * @param missingDependencies every dependency the plan should have but doesn't; never
 *     {@code null}
 */
@JsonClassDescription("A critique of a task plan")
public record TaskPlanRefinementVerdict(
    @JsonPropertyDescription(
        "True only if the plan has a problem other than missing dependencies that requires "
            + "re-planning: a task that doesn't serve the objective, redundant or overlapping "
            + "tasks, or a request that was never really decomposed. Missing dependencies alone "
            + "are not a reason to set this -- list them in missingDependencies instead.")
    boolean needsRevision,

    @JsonPropertyDescription(
        "Your reasoning. When needsRevision is true, concrete guidance on what to change.")
    String reasoning,

    @JsonPropertyDescription(
        "Every dependency the plan is missing: a task that cannot start until another finishes, "
            + "where the plan doesn't say so. Empty if none are missing.")
    List<MissingDependency> missingDependencies) {

    /**
     * Normalizes an absent list -- a model that found nothing missing may omit the field -- to
     * an empty one.
     */
    public TaskPlanRefinementVerdict {
        missingDependencies = Objects.requireNonNullElse(missingDependencies, List.of());
    }

    /**
     * A verdict that reports no missing dependencies.
     *
     * @param needsRevision whether the plan needs re-planning
     * @param reasoning why
     */
    public TaskPlanRefinementVerdict(final boolean needsRevision, final String reasoning) {
        this(needsRevision, reasoning, List.of());
    }

    /**
     * One dependency a task plan is missing, named by task titles.
     *
     * @param task the exact title of the task that must wait
     * @param dependsOn the exact titles of the tasks it must wait for
     */
    public record MissingDependency(
        @JsonPropertyDescription("The exact title of the task that must wait")
        String task,

        @JsonPropertyDescription("The exact titles of the tasks it must wait for")
        List<String> dependsOn) {
    }
}
