package org.vader.common.model.vader.queue;

/**
 * The egress side of a queue's back pressure: how many records may be processed concurrently and
 * how many are in flight right now.
 */
public class Egress {

    private Integer maxOpenMessages = 1;

    private Integer currentOpenMessages = 0;

    /**
     * The maximum number of records the consumer will process concurrently.
     *
     * @return the concurrency ceiling
     */
    public Integer getMaxOpenMessages() {
        return this.maxOpenMessages;
    }

    public void setMaxOpenMessages(Integer maxOpenMessages) {
        this.maxOpenMessages = maxOpenMessages;
    }

    /**
     * The number of records currently claimed and being processed.
     *
     * @return the in-flight record count
     */
    public Integer getCurrentOpenMessages() {
        return this.currentOpenMessages;
    }

    public void setCurrentOpenMessages(Integer currentOpenMessages) {
        this.currentOpenMessages = currentOpenMessages;
    }
}
