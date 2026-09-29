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
import java.util.Objects;
import java.util.Set;

/**
 * JPA entity representing a single node in a {@link TaskGraphEntity}.
 *
 * <p>Each task points to the parent it was decomposed from (if any) and the subtasks it was
 * further decomposed into. Separately, a task may also depend on other tasks completing first
 * -- e.g. a fan-in node with more than one direct predecessor -- tracked via {@code dependsOn}.
 *
 * <p>Only root tasks sit directly on the {@link TaskGraphEntity}; a subtask's {@code taskGraph} is
 * {@code null} and it is reached through its parent -- use {@link #owningTaskGraph()} to get from
 * any task to its graph. A subtask created at runtime, when an evaluator decomposed an attempt's
 * unfinished work, also records that attempt as {@code spawnedByAttempt}.</p>
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_spawned_by_attempt_join_id")
    private TaskAttemptEntity spawnedByAttempt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "task_dependency_join",
        joinColumns = @JoinColumn(name = "task_join_id"),
        inverseJoinColumns = @JoinColumn(name = "depends_on_task_join_id"))
    private Set<TaskEntity> dependsOn = new LinkedHashSet<>();

    @ManyToMany(mappedBy = "dependsOn", fetch = FetchType.LAZY)
    private Set<TaskEntity> dependents = new LinkedHashSet<>();

    @OneToMany(mappedBy = "task", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private Set<TaskUpdateEntity> taskUpdates = new LinkedHashSet<>();

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

    public TaskAttemptEntity getSpawnedByAttempt() {
        return this.spawnedByAttempt;
    }

    public void setSpawnedByAttempt(TaskAttemptEntity spawnedByAttempt) {
        this.spawnedByAttempt = spawnedByAttempt;
    }

    /**
     * The graph this task belongs to, whether it is a root task (held directly) or a subtask at
     * any depth (held by its root ancestor).
     *
     * @return the owning task graph
     */
    public TaskGraphEntity owningTaskGraph() {
        return Objects.isNull(this.parentTask) ? this.taskGraph : this.parentTask.owningTaskGraph();
    }

    /**
     * How many ancestors this task has: {@code 0} for a root task.
     *
     * @return the task's depth in its decomposition tree
     */
    public int depth() {
        return Objects.isNull(this.parentTask) ? 0 : this.parentTask.depth() + 1;
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

    public Set<TaskUpdateEntity> getTaskUpdates() {
        return this.taskUpdates;
    }

    public void setTaskUpdates(Set<TaskUpdateEntity> taskUpdates) {
        this.taskUpdates = taskUpdates;
    }
}
