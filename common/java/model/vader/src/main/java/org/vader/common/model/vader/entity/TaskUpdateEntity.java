package org.vader.common.model.vader.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;

/**
 * JPA entity recording a single update against a {@link TaskEntity} -- e.g. an evaluator's
 * pass/fail verdict on one particular attempt, or the orchestrator's own note on why a failure
 * happened and whether it re-dispatched the task. Kept as its own entity, rather than fields on
 * {@link TaskEntity}, for the same reason {@link TaskAttemptEntity} is: a task accumulates a
 * history of updates across however many attempts it takes, and that history must survive every
 * one of them. Every task gets at least one, the moment it's persisted -- see
 * {@link TaskUpdateType#CREATED} -- rather than only ever accumulating updates once something
 * goes wrong.
 *
 * <p>{@code taskAttempt} is optional but, in practice, almost always set: a task can accumulate
 * several attempts, and without knowing which one an update is about, nothing could tell "attempt
 * 2 was evaluated" apart from a stale verdict left over from attempt 1. {@code CREATED} is the one
 * exception -- it's written before any attempt exists.</p>
 *
 * <p>{@code author} names which role wrote the update; see {@link TaskUpdateAuthor}.</p>
 */
@Entity
public class TaskUpdateEntity extends AbstractModelEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_update_task_join_id")
    private TaskEntity task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_update_task_attempt_join_id")
    private TaskAttemptEntity taskAttempt;

    @NotNull
    @Enumerated(EnumType.STRING)
    private TaskUpdateType type;

    @NotNull
    @Enumerated(EnumType.STRING)
    private TaskUpdateAuthor author;

    @Lob
    @NotNull
    private String description;

    @Override
    public String getModelType() {
        return "TaskUpdate";
    }

    public TaskEntity getTask() {
        return this.task;
    }

    public void setTask(TaskEntity task) {
        this.task = task;
    }

    public TaskAttemptEntity getTaskAttempt() {
        return this.taskAttempt;
    }

    public void setTaskAttempt(TaskAttemptEntity taskAttempt) {
        this.taskAttempt = taskAttempt;
    }

    public TaskUpdateType getType() {
        return this.type;
    }

    public void setType(TaskUpdateType type) {
        this.type = type;
    }

    public TaskUpdateAuthor getAuthor() {
        return this.author;
    }

    public void setAuthor(TaskUpdateAuthor author) {
        this.author = author;
    }

    public String getDescription() {
        return this.description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
