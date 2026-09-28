package org.vader.common.model.vader.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;

/**
 * JPA entity logging a single inference turn taken while a {@link TaskAttemptEntity} was
 * running: the prompt sent to the model and the response it returned, over the
 * {@code /agent/inference} gateway. Every harness turn is persisted here -- this is the durable
 * transcript backing "log everything from prompt to workflow finished."
 *
 * <p>{@code prompt} holds only the messages newly appended to the running conversation since the
 * previous turn -- not the whole conversation, which the harness resends in full on every call.
 * The full conversation for an attempt is reconstructable by concatenating every row's
 * {@code prompt} in {@code turnIndex} order; {@code messageCount} is the cumulative running
 * total as of this turn, letting the next turn know where its own delta starts.</p>
 */
@Entity
public class TaskAttemptTranscriptEntity extends AbstractModelEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_attempt_transcript_task_attempt_join_id")
    private TaskAttemptEntity taskAttempt;

    @NotNull
    private Integer turnIndex;

    @Lob
    @NotNull
    private String prompt;

    @NotNull
    private Integer messageCount;

    @Lob
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

    public TaskAttemptEntity getTaskAttempt() {
        return this.taskAttempt;
    }

    public void setTaskAttempt(TaskAttemptEntity taskAttempt) {
        this.taskAttempt = taskAttempt;
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
