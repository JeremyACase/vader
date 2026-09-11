package org.vader.common.model.vader.dto;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import org.vader.common.model.vader.entity.TaskAttemptStatus;

/**
 * DTO representing a single dispatch of a task to an agent harness: the durable audit record of
 * what was attempted, when, and how it ended.
 *
 * <p>{@code taskId} is a shallow reference -- see {@link Task#getParentTaskId()} for why the
 * model conventions here favor ids over embedding.</p>
 */
public class TaskAttempt extends AbstractModel {

    @NotNull
    private String taskId;

    @NotNull
    private Integer attemptNumber;

    @NotNull
    private TaskAttemptStatus status;

    private OffsetDateTime dispatchedAt;

    private OffsetDateTime startedAt;

    private OffsetDateTime lastHeartbeatAt;

    private OffsetDateTime completedAt;

    @NotNull
    private Integer turnsUsed;

    @NotNull
    private Long tokensUsed;

    private String result;

    private String failureReason;

    @Override
    public String getModelType() {
        return "TaskAttempt";
    }

    public String getTaskId() {
        return this.taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
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
