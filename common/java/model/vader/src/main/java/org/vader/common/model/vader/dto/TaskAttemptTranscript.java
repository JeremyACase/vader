package org.vader.common.model.vader.dto;

import jakarta.validation.constraints.NotNull;

/**
 * DTO representing one logged inference turn: the prompt sent to the model and the response it
 * returned, while a {@link TaskAttempt} was running.
 *
 * <p>{@code taskAttemptId} is a shallow reference, same convention as every other cross-model
 * reference in this package. {@code prompt} holds only the messages newly appended since the
 * previous turn -- concatenate every turn's {@code prompt} in {@code turnIndex} order to
 * reconstruct the full conversation; {@code messageCount} is the cumulative running total as of
 * this turn.</p>
 */
public class TaskAttemptTranscript extends AbstractModel {

    @NotNull
    private String taskAttemptId;

    @NotNull
    private Integer turnIndex;

    @NotNull
    private String prompt;

    @NotNull
    private Integer messageCount;

    @NotNull
    private String response;

    @NotNull
    private Long tokensSpent;

    /**
     * Why the model stopped generating this turn, as the provider reported it -- e.g. Ollama's
     * {@code stop} or {@code length} (cut off at the output token cap). {@code null} for turns
     * recorded before this was captured, or when the provider reports none.
     */
    private String finishReason;

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

    public Integer getMessageCount() {
        return this.messageCount;
    }

    public void setMessageCount(Integer messageCount) {
        this.messageCount = messageCount;
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

    public String getFinishReason() {
        return this.finishReason;
    }

    public void setFinishReason(String finishReason) {
        this.finishReason = finishReason;
    }
}
