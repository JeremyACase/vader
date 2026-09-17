package org.vader.common.model.vader.dto;

import jakarta.validation.constraints.NotNull;

/**
 * DTO representing one logged tool call: the tool a model asked for, the arguments it was called
 * with, and the result it returned, while a {@link TaskAttempt} was running.
 *
 * <p>{@code taskAttemptId} is a shallow reference, same convention as every other cross-model
 * reference in this package.</p>
 */
public class TaskAttemptToolCall extends AbstractModel {

    @NotNull
    private String taskAttemptId;

    @NotNull
    private String toolCallId;

    @NotNull
    private String toolName;

    @NotNull
    private String argumentsJson;

    @NotNull
    private String resultJson;

    @Override
    public String getModelType() {
        return "TaskAttemptToolCall";
    }

    public String getTaskAttemptId() {
        return this.taskAttemptId;
    }

    public void setTaskAttemptId(String taskAttemptId) {
        this.taskAttemptId = taskAttemptId;
    }

    public String getToolCallId() {
        return this.toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    public String getToolName() {
        return this.toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getArgumentsJson() {
        return this.argumentsJson;
    }

    public void setArgumentsJson(String argumentsJson) {
        this.argumentsJson = argumentsJson;
    }

    public String getResultJson() {
        return this.resultJson;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
    }
}
