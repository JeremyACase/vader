package org.vader.common.model.vader.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;

/**
 * Queue message carrying a {@link TaskAttemptEntity} that is ready to be dispatched to an agent
 * harness. {@code TaskAssignmentInbox} drains these and asks the agent-harness operator to
 * create a Kubernetes Job for each.
 *
 * <p>This message's own {@code PENDING}/{@code CLAIMED}/{@code PROCESSED}/{@code FAILED}
 * lifecycle only tracks <em>dispatch</em> -- whether a Job was successfully created. It says
 * nothing about whether the harness itself ultimately succeeded; that is tracked separately and
 * durably on the referenced {@link TaskAttemptEntity}, updated by the harness's own heartbeat and
 * result calls over the agent control plane.</p>
 */
@Entity
public class TaskAssignmentOutboxMessageEntity extends AbstractOutboxMessageEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_assignment_outbox_message_task_attempt_join_id")
    private TaskAttemptEntity taskAttempt;

    @Override
    public String getModelType() {
        return "TaskAssignmentOutboxMessage";
    }

    public TaskAttemptEntity getTaskAttempt() {
        return this.taskAttempt;
    }

    public void setTaskAttempt(TaskAttemptEntity taskAttempt) {
        this.taskAttempt = taskAttempt;
    }
}
