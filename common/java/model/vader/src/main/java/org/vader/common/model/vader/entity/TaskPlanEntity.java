package org.vader.common.model.vader.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToOne;
import jakarta.validation.constraints.NotNull;

/** JPA entity representing a problem decomposed into actionable tasks by the orchestrator LLM. */
@Entity
public class TaskPlanEntity extends AbstractModelEntity {

    @Lob
    @NotNull
    private String objective;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_plan_workflow_join_id")
    private WorkflowEntity workflow;

    @OneToOne(mappedBy = "taskPlan", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private TaskGraphEntity taskGraph;

    @Override
    public String getModelType() {
        return "TaskPlan";
    }

    public String getObjective() {
        return this.objective;
    }

    public void setObjective(String objective) {
        this.objective = objective;
    }

    public WorkflowEntity getWorkflow() {
        return this.workflow;
    }

    public void setWorkflow(WorkflowEntity workflow) {
        this.workflow = workflow;
    }

    public TaskGraphEntity getTaskGraph() {
        return this.taskGraph;
    }

    public void setTaskGraph(TaskGraphEntity taskGraph) {
        this.taskGraph = taskGraph;
    }
}
