package org.vader.core.server.service.backpressure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.vader.common.model.vader.queue.BackPressure;
import org.vader.common.model.vader.queue.Egress;
import org.vader.common.model.vader.queue.Ingress;

/**
 * Assembles a {@link BackPressure} snapshot for a queued model type from its live counters
 * (via the {@link BackpressureRegistry}) and its sampled rate of change (via the
 * {@link BackpressureSampler}). Mirrors ubiquia's {@code BackPressureCalculator}.
 */
@Service
public class BackpressureCalculator {

    private static final Logger logger = LoggerFactory.getLogger(BackpressureCalculator.class);

    @Autowired
    private BackpressureRegistry registry;

    @Autowired
    private BackpressureSampler sampler;

    /**
     * Builds the current back pressure snapshot for a queued model type.
     *
     * @param modelType the payload model type (e.g. {@code "ClientPrompt"})
     * @return the snapshot
     * @throws IllegalArgumentException if no queue is registered for {@code modelType}
     */
    public BackPressure calculateFor(final String modelType) {
        var queue = this.registry.require(modelType);

        var ingress = new Ingress();
        ingress.setQueuedRecords(queue.queuedRecordCount());
        ingress.setQueueRatePerMinute(this.sampler.ratePerMinuteFor(modelType));

        var egress = new Egress();
        egress.setMaxOpenMessages(queue.maxOpenMessages());
        egress.setCurrentOpenMessages((int) queue.openMessageCount());

        var backPressure = new BackPressure();
        backPressure.setIngress(ingress);
        backPressure.setEgress(egress);

        logger.debug("Back pressure for {}: queued={}, rate/min={}, open={}/{}",
            modelType, ingress.getQueuedRecords(), ingress.getQueueRatePerMinute(),
            egress.getCurrentOpenMessages(), egress.getMaxOpenMessages());
        return backPressure;
    }
}
