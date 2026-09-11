package org.vader.common.model.vader.queue;

/**
 * The ingress side of a queue's back pressure: how much work is waiting to be consumed, and how
 * fast that backlog is growing or shrinking.
 */
public class Ingress {

    private Long queuedRecords;

    private Float queueRatePerMinute;

    /**
     * The number of records currently queued and waiting to be processed.
     *
     * @return the queued record count
     */
    public Long getQueuedRecords() {
        return this.queuedRecords;
    }

    public void setQueuedRecords(Long queuedRecords) {
        this.queuedRecords = queuedRecords;
    }

    /**
     * The rate of change of the queued record count, per minute. Positive when the backlog is
     * growing, negative when it is draining faster than it fills.
     *
     * @return the queue rate per minute
     */
    public Float getQueueRatePerMinute() {
        return this.queueRatePerMinute;
    }

    public void setQueueRatePerMinute(Float queueRatePerMinute) {
        this.queueRatePerMinute = queueRatePerMinute;
    }
}
