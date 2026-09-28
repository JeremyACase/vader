package org.vader.common.model.vader.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;

/**
 * JPA entity logging a single tool call a model requested and a harness had core-server actually
 * execute, over the {@code /agent/tool-calls} gateway -- the arguments it was called with and the
 * result it returned. Written at the moment of execution, before the result is handed back to the
 * harness, so the record survives even if the harness never makes it to a following inference
 * turn (which would otherwise be the only other place this exchange gets folded in, as part of
 * that turn's {@link TaskAttemptTranscriptEntity#getPrompt()}).
 */
@Entity
public class TaskAttemptToolCallEntity extends AbstractModelEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_attempt_tool_call_task_attempt_join_id")
    private TaskAttemptEntity taskAttempt;

    @NotNull
    private String toolCallId;

    @NotNull
    private String toolName;

    @Lob
    @NotNull
    private String argumentsJson;

    @Lob
    @NotNull
    private String resultJson;

    @Override
    public String getModelType() {
        return "TaskAttemptToolCall";
    }

    public TaskAttemptEntity getTaskAttempt() {
        return this.taskAttempt;
    }

    public void setTaskAttempt(TaskAttemptEntity taskAttempt) {
        this.taskAttempt = taskAttempt;
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
