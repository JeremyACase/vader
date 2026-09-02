package org.vader.common.model.vader.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.validation.constraints.NotNull;

/** JPA entity representing a workflow spawned to service a client-submitted prompt. */
@Entity
public class WorkflowEntity extends AbstractModelEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_client_prompt_join_id")
    private ClientPromptEntity clientPrompt;

    @OneToOne(mappedBy = "workflow", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private TaskPlanEntity taskPlan;

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
}
