package org.vader.common.model.vader.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.validation.constraints.NotNull;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * JPA entity representing a single node in a {@link TaskGraphEntity}.
 *
 * <p>Each task points to the parent it was decomposed from (if any) and the subtasks it was
 * further decomposed into. Separately, a task may also depend on other tasks completing first
 * -- e.g. a fan-in node with more than one direct predecessor -- tracked via {@code dependsOn}.
 */
@Entity
public class TaskEntity extends AbstractModelEntity {

    @NotNull
    private String title;

    @Lob
    @NotNull
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_task_graph_join_id")
    private TaskGraphEntity taskGraph;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_parent_task_join_id")
    private TaskEntity parentTask;

    @OneToMany(mappedBy = "parentTask", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private Set<TaskEntity> subTasks = new LinkedHashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "task_dependency_join",
        joinColumns = @JoinColumn(name = "task_join_id"),
        inverseJoinColumns = @JoinColumn(name = "depends_on_task_join_id"))
    private Set<TaskEntity> dependsOn = new LinkedHashSet<>();

    @ManyToMany(mappedBy = "dependsOn", fetch = FetchType.LAZY)
    private Set<TaskEntity> dependents = new LinkedHashSet<>();

    @Override
    public String getModelType() {
        return "Task";
    }

    public String getTitle() {
        return this.title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return this.description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TaskGraphEntity getTaskGraph() {
        return this.taskGraph;
    }

    public void setTaskGraph(TaskGraphEntity taskGraph) {
        this.taskGraph = taskGraph;
    }

    public TaskEntity getParentTask() {
        return this.parentTask;
    }

    public void setParentTask(TaskEntity parentTask) {
        this.parentTask = parentTask;
    }

    public Set<TaskEntity> getSubTasks() {
        return this.subTasks;
    }

    public void setSubTasks(Set<TaskEntity> subTasks) {
        this.subTasks = subTasks;
    }

    public Set<TaskEntity> getDependsOn() {
        return this.dependsOn;
    }

    public void setDependsOn(Set<TaskEntity> dependsOn) {
        this.dependsOn = dependsOn;
    }

    public Set<TaskEntity> getDependents() {
        return this.dependents;
    }

    public void setDependents(Set<TaskEntity> dependents) {
        this.dependents = dependents;
    }
}
