package org.vader.common.model.vader.dto;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import org.vader.common.model.vader.entity.OutboxMessageStatus;

/**
 * Abstract base DTO for a message on an inbox/outbox queue: where it is in its
 * {@code PENDING -> CLAIMED -> PROCESSED | FAILED} lifecycle. Concrete subclasses add the payload
 * the message carries.
 */
public abstract class AbstractOutboxMessage extends AbstractModel {

    @NotNull
    private OutboxMessageStatus status;

    private OffsetDateTime claimedAt;

    private OffsetDateTime processedAt;

    private int attempts;

    private String failureReason;

    public OutboxMessageStatus getStatus() {
        return this.status;
    }

    public void setStatus(OutboxMessageStatus status) {
        this.status = status;
    }

    public OffsetDateTime getClaimedAt() {
        return this.claimedAt;
    }

    public void setClaimedAt(OffsetDateTime claimedAt) {
        this.claimedAt = claimedAt;
    }

    public OffsetDateTime getProcessedAt() {
        return this.processedAt;
    }

    public void setProcessedAt(OffsetDateTime processedAt) {
        this.processedAt = processedAt;
    }

    public int getAttempts() {
        return this.attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public String getFailureReason() {
        return this.failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }
}
