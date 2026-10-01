package org.vader.common.model.vader.dto;

/**
 * DTO for a queue message carrying a task attempt awaiting dispatch to an agent harness. The
 * attempt is a shallow id reference.
 */
public class TaskAssignmentOutboxMessage extends AbstractOutboxMessage {

    private String taskAttemptId;

    @Override
    public String getModelType() {
        return "TaskAssignmentOutboxMessage";
    }

    public String getTaskAttemptId() {
        return this.taskAttemptId;
    }

    public void setTaskAttemptId(String taskAttemptId) {
        this.taskAttemptId = taskAttemptId;
    }
}
