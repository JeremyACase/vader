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

    @Lob
    @NotNull
    private String response;

    @NotNull
    private Long tokensSpent;

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
