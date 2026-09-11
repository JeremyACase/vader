package org.vader.common.model.vader.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Lob;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

/**
 * Abstract base JPA entity for a message on an inbox/outbox queue.
 *
 * <p>A concrete subclass adds a reference to the payload it carries (e.g. a client prompt). The
 * outbox writes rows here in {@code PENDING}; the inbox claims them ({@code CLAIMED}) and settles
 * them to {@code PROCESSED} or {@code FAILED}. These rows are intentionally not exposed through a
 * DAO controller -- callers observe the queue via the back pressure endpoint, not by querying
 * individual messages.</p>
 */
@Entity
public abstract class AbstractOutboxMessageEntity extends AbstractModelEntity {

    @NotNull
    @Enumerated(EnumType.STRING)
    private OutboxMessageStatus status = OutboxMessageStatus.PENDING;

    private OffsetDateTime claimedAt;

    private OffsetDateTime processedAt;

    private int attempts = 0;

    @Lob
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
