package org.vader.common.model.vader.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import org.vader.common.model.vader.entity.WorkflowStatus;

/**
 * DTO representing a workflow spawned to service a client-submitted prompt.
 *
 * <p>{@code clientPromptId} is a shallow reference to the originating {@link ClientPrompt}
 * rather than an embedded copy, since that prompt's attached files aren't meaningful to
 * re-serialize here. {@code result} is the single answer synthesized from every task's own
 * result once the workflow reaches a terminal status -- not just a list of what each task did.
 */
public class Workflow extends AbstractModel {

    @NotNull
    private String clientPromptId;

    @Valid
    private TaskPlan taskPlan;

    @NotNull
    private WorkflowStatus status;

    private OffsetDateTime completedAt;

    private String result;

    @Override
    public String getModelType() {
        return "Workflow";
    }

    public String getClientPromptId() {
        return this.clientPromptId;
    }

    public void setClientPromptId(String clientPromptId) {
        this.clientPromptId = clientPromptId;
    }

    public TaskPlan getTaskPlan() {
        return this.taskPlan;
    }

    public void setTaskPlan(TaskPlan taskPlan) {
        this.taskPlan = taskPlan;
    }

    public WorkflowStatus getStatus() {
        return this.status;
    }

    public void setStatus(WorkflowStatus status) {
        this.status = status;
    }

    public OffsetDateTime getCompletedAt() {
        return this.completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public String getResult() {
        return this.result;
    }

    public void setResult(String result) {
        this.result = result;
    }
}
