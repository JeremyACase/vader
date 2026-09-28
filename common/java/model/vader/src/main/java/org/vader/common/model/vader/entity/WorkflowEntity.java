package org.vader.common.model.vader.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

/**
 * JPA entity representing a workflow spawned to service a client-submitted prompt.
 *
 * <p>{@code status} starts {@code RUNNING} as soon as the workflow (and its task graph) is
 * persisted, and is driven to a terminal value by {@code TaskGraphScheduler} once every task in
 * the graph has reached a terminal {@link TaskAttemptStatus}. {@code result} is populated at the
 * same time -- a single answer synthesized from every task's own result, not just a list of what
 * each task did.</p>
 */
@Entity
public class WorkflowEntity extends AbstractModelEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_client_prompt_join_id")
    private ClientPromptEntity clientPrompt;

    @OneToOne(mappedBy = "workflow", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private TaskPlanEntity taskPlan;

    @NotNull
    @Enumerated(EnumType.STRING)
    private WorkflowStatus status = WorkflowStatus.RUNNING;

    private OffsetDateTime completedAt;

    @Lob
    private String result;

    @Override
    public String getModelType() {
        return "Workflow";
    }

    public ClientPromptEntity getClientPrompt() {
        return this.clientPrompt;
    }

    public void setClientPrompt(ClientPromptEntity clientPrompt) {
        this.clientPrompt = clientPrompt;
    }

    public TaskPlanEntity getTaskPlan() {
        return this.taskPlan;
    }

    public void setTaskPlan(TaskPlanEntity taskPlan) {
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
