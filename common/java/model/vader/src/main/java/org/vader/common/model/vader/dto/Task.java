package org.vader.common.model.vader.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO representing a single node of a {@link TaskGraph}: an actionable task decomposed from a
 * {@link TaskPlan}.
 *
 * <p>{@code parentTaskId} is a shallow reference to the task this one was decomposed from (root
 * tasks leave it {@code null}); embedding the parent object here, rather than referencing it by
 * id, would recurse back through this task's own {@code subTasks}. {@code dependsOnTaskIds} are
 * shallow references too, for the same reason -- they're graph edges, not owned children.
 */
public class Task extends AbstractModel {

    @NotNull
    private String title;

    @NotNull
    private String description;

    private String parentTaskId;

    private List<@Valid Task> subTasks = new ArrayList<>();

    private List<String> dependsOnTaskIds = new ArrayList<>();

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

    public String getParentTaskId() {
        return this.parentTaskId;
    }

    public void setParentTaskId(String parentTaskId) {
        this.parentTaskId = parentTaskId;
    }

    public List<Task> getSubTasks() {
        return this.subTasks;
    }

    public void setSubTasks(List<Task> subTasks) {
        this.subTasks = subTasks;
    }

    public List<String> getDependsOnTaskIds() {
        return this.dependsOnTaskIds;
    }

    public void setDependsOnTaskIds(List<String> dependsOnTaskIds) {
        this.dependsOnTaskIds = dependsOnTaskIds;
    }
}
