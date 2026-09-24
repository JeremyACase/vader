package org.vader.common.model.vader.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

/**
 * Queue message carrying a {@link TaskAttemptEntity} that has gone terminal and needs review --
 * an evaluator verdict and, on failure, an orchestrator reattempt decision. {@code
 * TaskAttemptReviewInbox} drains these off {@code TaskGraphScheduler}'s own thread, since review
 * involves LLM calls that must not block workflow-progress bookkeeping.
 *
 * <p>This message's own {@code PENDING}/{@code CLAIMED}/{@code PROCESSED}/{@code FAILED}
 * lifecycle only tracks whether review ran -- the actual verdict and reasoning are recorded
 * durably as {@link TaskUpdateEntity} rows against the referenced attempt.</p>
 *
 * <p>A review that fails because the LLM is unavailable is not failed: it goes back to
 * {@code PENDING} with {@link #getNextAttemptAt()} set, and is not claimed again until then.</p>
 */
@Entity
public class TaskAttemptReviewOutboxMessageEntity extends AbstractOutboxMessageEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_attempt_review_outbox_message_task_attempt_join_id")
    private TaskAttemptEntity taskAttempt;

    private OffsetDateTime nextAttemptAt;

    @Override
    public String getModelType() {
        return "TaskAttemptReviewOutboxMessage";
    }

    public TaskAttemptEntity getTaskAttempt() {
        return this.taskAttempt;
    }

    public void setTaskAttempt(TaskAttemptEntity taskAttempt) {
        this.taskAttempt = taskAttempt;
    }

    /**
     * When a deferred review may next be claimed; {@code null} if it may be claimed immediately.
     *
     * @return the earliest next claim time, or {@code null}
     */
    public OffsetDateTime getNextAttemptAt() {
        return this.nextAttemptAt;
    }

    public void setNextAttemptAt(OffsetDateTime nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }
}
