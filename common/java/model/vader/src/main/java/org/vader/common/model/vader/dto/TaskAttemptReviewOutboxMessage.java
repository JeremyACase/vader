package org.vader.common.model.vader.dto;

import java.time.OffsetDateTime;

/**
 * DTO for a queue message carrying a settled task attempt awaiting review. The attempt is a
 * shallow id reference; {@code nextAttemptAt} is set while a review deferred by an LLM outage
 * waits to be claimed again.
 */
public class TaskAttemptReviewOutboxMessage extends AbstractOutboxMessage {

    private String taskAttemptId;

    private OffsetDateTime nextAttemptAt;

    @Override
    public String getModelType() {
        return "TaskAttemptReviewOutboxMessage";
    }

    public String getTaskAttemptId() {
        return this.taskAttemptId;
    }

    public void setTaskAttemptId(String taskAttemptId) {
        this.taskAttemptId = taskAttemptId;
    }

    public OffsetDateTime getNextAttemptAt() {
        return this.nextAttemptAt;
    }

    public void setNextAttemptAt(OffsetDateTime nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }
}
