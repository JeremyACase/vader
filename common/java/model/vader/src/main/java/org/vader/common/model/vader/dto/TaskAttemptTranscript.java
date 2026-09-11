package org.vader.common.model.vader.dto;

import jakarta.validation.constraints.NotNull;

/**
 * DTO representing one logged inference turn: the prompt sent to the model and the response it
 * returned, while a {@link TaskAttempt} was running.
 *
 * <p>{@code taskAttemptId} is a shallow reference, same convention as every other cross-model
 * reference in this package.</p>
 */
public class TaskAttemptTranscript extends AbstractModel {

    @NotNull
    private String taskAttemptId;

    @NotNull
    private Integer turnIndex;

    @NotNull
    private String prompt;

    @NotNull
    private String response;

    @NotNull
    private Long tokensSpent;

    @Override
    public String getModelType() {
        return "TaskAttemptTranscript";
    }

    public String getTaskAttemptId() {
        return this.taskAttemptId;
    }

    public void setTaskAttemptId(String taskAttemptId) {
        this.taskAttemptId = taskAttemptId;
    }

    public Integer getTurnIndex() {
        return this.turnIndex;
    }

    public void setTurnIndex(Integer turnIndex) {
        this.turnIndex = turnIndex;
    }

    public String getPrompt() {
        return this.prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getResponse() {
        return this.response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public Long getTokensSpent() {
        return this.tokensSpent;
    }

    public void setTokensSpent(Long tokensSpent) {
        this.tokensSpent = tokensSpent;
    }
}
