package org.vader.common.model.vader.entity;

/**
 * The kind of event a {@link TaskUpdateEntity} records against a {@link TaskEntity}.
 *
 * <ul>
 *   <li>{@code CREATED} -- the task was just persisted as part of a workflow's decomposition.</li>
 *   <li>{@code RUNNING} -- an attempt's harness made first contact and started working it.</li>
 *   <li>{@code UPDATE} -- an interim progress note, not itself a verdict on the task.</li>
 *   <li>{@code COMPLETED} -- an evaluator judged the task's latest attempt a pass.</li>
 *   <li>{@code FAILED} -- an evaluator judged the task's latest attempt a fail.</li>
 *   <li>{@code TIMED_OUT} -- the task's latest attempt ran out its deadline before finishing.</li>
 *   <li>{@code DECOMPOSED} -- an evaluator judged the task's latest attempt real but unfinished
 *       progress, and its remaining work was split into runtime subtasks. Not terminal: the task
 *       completes (or fails) once those subtasks settle.</li>
 * </ul>
 */
public enum TaskUpdateType {
    CREATED,
    RUNNING,
    UPDATE,
    COMPLETED,
    FAILED,
    TIMED_OUT,
    DECOMPOSED
}
