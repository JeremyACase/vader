package org.vader.common.model.vader.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

/**
 * JPA entity representing a single dispatch of a {@link TaskEntity} to an agent harness.
 *
 * <p>A task may accumulate several attempts (retries after {@code FAILED} / {@code TIMED_OUT} /
 * {@code STALLED}, up to a configured cap), so this is intentionally its own entity rather than
 * status fields on {@link TaskEntity} -- it keeps the task graph an immutable plan and preserves
 * full retry history. This is also the primary audit record for "what actually happened": every
 * status transition and its timing is durable and queryable, and every inference turn taken
 * while this attempt was running is logged separately as a {@link TaskAttemptTranscriptEntity}.
 */
@Entity
public class TaskAttemptEntity extends AbstractModelEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_attempt_task_join_id")
    private TaskEntity task;

    @NotNull
    private Integer attemptNumber;

    @NotNull
    @Enumerated(EnumType.STRING)
    private TaskAttemptStatus status = TaskAttemptStatus.PENDING;

    private OffsetDateTime dispatchedAt;

    private OffsetDateTime startedAt;

    private OffsetDateTime lastHeartbeatAt;

    private OffsetDateTime completedAt;

    @NotNull
    private Integer turnsUsed = 0;

    @NotNull
    private Long tokensUsed = 0L;

    @Lob
    private String result;

    @Lob
    private String failureReason;

    @Override
    public String getModelType() {
        return "TaskAttempt";
    }

    public TaskEntity getTask() {
        return this.task;
    }

    public void setTask(TaskEntity task) {
        this.task = task;
    }

    public Integer getAttemptNumber() {
        return this.attemptNumber;
    }

    public void setAttemptNumber(Integer attemptNumber) {
        this.attemptNumber = attemptNumber;
    }

    public TaskAttemptStatus getStatus() {
        return this.status;
    }

    public void setStatus(TaskAttemptStatus status) {
        this.status = status;
    }

    public OffsetDateTime getDispatchedAt() {
        return this.dispatchedAt;
    }

    public void setDispatchedAt(OffsetDateTime dispatchedAt) {
        this.dispatchedAt = dispatchedAt;
    }

    public OffsetDateTime getStartedAt() {
        return this.startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getLastHeartbeatAt() {
        return this.lastHeartbeatAt;
    }

    public void setLastHeartbeatAt(OffsetDateTime lastHeartbeatAt) {
        this.lastHeartbeatAt = lastHeartbeatAt;
    }

    public OffsetDateTime getCompletedAt() {
        return this.completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public Integer getTurnsUsed() {
        return this.turnsUsed;
    }

    public void setTurnsUsed(Integer turnsUsed) {
        this.turnsUsed = turnsUsed;
    }

    public Long getTokensUsed() {
        return this.tokensUsed;
    }

    public void setTokensUsed(Long tokensUsed) {
        this.tokensUsed = tokensUsed;
    }

    public String getResult() {
        return this.result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getFailureReason() {
        return this.failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }
}
