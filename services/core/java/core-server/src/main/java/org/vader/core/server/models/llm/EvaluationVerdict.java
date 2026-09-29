package org.vader.core.server.models.llm;

import java.util.List;
import java.util.Objects;

/**
 * An evaluator's independent verdict on one settled attempt. Doubles as the structured-output
 * shape a local LLM is asked to produce -- there is no lean-vs-full distinction to make here,
 * unlike decomposition's {@code LlmTaskPlan}.
 *
 * @param passed {@code true} if the evaluator judges the attempt to have actually succeeded
 * @param reasoning why -- persisted as the resulting {@code TaskUpdate}'s description
 * @param remainingSubtasks when not passed but real progress was made, the concrete steps still
 *     left to do, in order; empty otherwise. Never {@code null} -- a model that omits the field
 *     yields an empty list
 */
public record EvaluationVerdict(
    boolean passed, String reasoning, List<RemainingSubtask> remainingSubtasks) {

    /** Normalizes an omitted {@code remainingSubtasks} to an empty list. */
    public EvaluationVerdict {
        remainingSubtasks = Objects.isNull(remainingSubtasks) ? List.of() : remainingSubtasks;
    }

    /**
     * A verdict with no remaining steps -- a plain pass or fail.
     *
     * @param passed whether the attempt actually succeeded
     * @param reasoning why
     */
    public EvaluationVerdict(final boolean passed, final String reasoning) {
        this(passed, reasoning, List.of());
    }
}
