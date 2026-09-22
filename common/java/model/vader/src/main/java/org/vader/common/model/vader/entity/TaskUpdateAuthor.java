package org.vader.common.model.vader.entity;

/**
 * Which role authored a {@link TaskUpdateEntity} -- kept distinct from {@link TaskUpdateType}
 * because a single type can be written by more than one author (e.g. {@code UPDATE} covers both
 * a task agent's own progress notes and an orchestrator's reattempt reasoning).
 *
 * <ul>
 *   <li>{@code SYSTEM} -- mechanical bookkeeping with no agent judgment involved: a task's
 *       creation, or a deterministic timeout/stall verdict the reaper detected directly.</li>
 *   <li>{@code TASK_AGENT} -- the harness executing the task: its own interim progress notes, and
 *       the moment it first makes contact and starts running.</li>
 *   <li>{@code ORCHESTRATOR} -- the orchestrator agent's reattempt reasoning, whether reached by
 *       LLM judgment or a deterministic policy (e.g. the attempt cap).</li>
 *   <li>{@code EVALUATOR} -- the evaluator agent's independent pass/fail judgment on a
 *       self-reported outcome.</li>
 * </ul>
 */
public enum TaskUpdateAuthor {
    SYSTEM,
    TASK_AGENT,
    ORCHESTRATOR,
    EVALUATOR
}
