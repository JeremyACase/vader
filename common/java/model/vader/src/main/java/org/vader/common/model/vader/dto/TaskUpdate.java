package org.vader.common.model.vader.dto;

import jakarta.validation.constraints.NotNull;
import org.vader.common.model.vader.entity.TaskUpdateAuthor;
import org.vader.common.model.vader.entity.TaskUpdateType;

/**
 * DTO representing a single evaluator/orchestrator/task-agent-authored update against a task.
 *
 * <p>{@code taskId} and {@code taskAttemptId} are shallow references -- see
 * {@link Task#getParentTaskId()} for why the model conventions here favor ids over embedding.</p>
 */
public class TaskUpdate extends AbstractModel {

    @NotNull
    private String taskId;

    private String taskAttemptId;

    @NotNull
    private TaskUpdateType type;

    @NotNull
    private TaskUpdateAuthor author;

    @NotNull
    private String description;

    @Override
    public String getModelType() {
        return "TaskUpdate";
    }

    public String getTaskId() {
        return this.taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getTaskAttemptId() {
        return this.taskAttemptId;
    }

    public void setTaskAttemptId(String taskAttemptId) {
        this.taskAttemptId = taskAttemptId;
    }

    public TaskUpdateType getType() {
        return this.type;
    }

    public void setType(TaskUpdateType type) {
        this.type = type;
    }

    public TaskUpdateAuthor getAuthor() {
        return this.author;
    }

    public void setAuthor(TaskUpdateAuthor author) {
        this.author = author;
    }

    public String getDescription() {
        return this.description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
