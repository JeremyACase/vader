package org.vader.common.model.vader.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * DTO representing a workflow spawned to service a client-submitted prompt.
 *
 * <p>{@code clientPromptId} is a shallow reference to the originating {@link ClientPrompt}
 * rather than an embedded copy, since that prompt's attached files aren't meaningful to
 * re-serialize here.
 */
public class Workflow extends AbstractModel {

    @NotNull
    private String clientPromptId;

    @Valid
    private TaskPlan taskPlan;

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
}
