package org.vader.core.server.service.backpressure;

/**
 * Exposes the counters a {@link BackpressureCalculator} needs to describe the load on a single
 * inbox/outbox queue. Implemented by each concrete inbox.
 */
public interface InterfaceQueueBackpressure {

    /**
     * The {@code modelType} of the payload this queue carries (e.g. {@code "ClientPrompt"}).
     *
     * @return the queued payload model type
     */
    String queuedModelType();

    /**
     * The number of messages waiting to be claimed.
     *
     * @return the pending message count
     */
    long queuedRecordCount();

    /**
     * The number of messages currently claimed and being processed.
     *
     * @return the in-flight message count
     */
    long openMessageCount();

    /**
     * The maximum number of messages this queue will process concurrently.
     *
     * @return the concurrency ceiling
     */
    int maxOpenMessages();
}
