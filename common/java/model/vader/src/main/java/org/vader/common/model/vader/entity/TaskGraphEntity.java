package org.vader.common.model.vader.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * JPA entity representing the directed-acyclic-graph of tasks built from a
 * {@link TaskPlanEntity}.
 */
@Entity
public class TaskGraphEntity extends AbstractModelEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_graph_task_plan_join_id")
    private TaskPlanEntity taskPlan;

    @OneToMany(mappedBy = "taskGraph", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private Set<TaskEntity> tasks = new LinkedHashSet<>();

    @Override
    public String getModelType() {
        return "TaskGraph";
    }

    public TaskPlanEntity getTaskPlan() {
        return this.taskPlan;
    }

    public void setTaskPlan(TaskPlanEntity taskPlan) {
        this.taskPlan = taskPlan;
    }

    public Set<TaskEntity> getTasks() {
        return this.tasks;
    }

    public void setTasks(Set<TaskEntity> tasks) {
        this.tasks = tasks;
    }
}
