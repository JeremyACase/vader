package org.vader.core.server.service.backpressure;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically samples the pending depth of every registered queue and keeps the last two
 * samples per model type, so a rate of change can be reported alongside the instantaneous depth.
 *
 * <p>Two data points is enough for a first-derivative estimate; the window is deliberately
 * small. Mirrors ubiquia's {@code pollToSampleBackPressure()} plus
 * {@code BackPressureCalculator.getRate()}.</p>
 */
@Component
public class BackpressureSampler {

    private static final int WINDOW = 2;
    private static final float MILLIS_PER_MINUTE = 60_000f;

    private static final Logger logger = LoggerFactory.getLogger(BackpressureSampler.class);

    @Autowired
    private BackpressureRegistry registry;

    @Value("${vader.backpressure.sample-interval-ms:15000}")
    private long sampleIntervalMs;

    private final Map<String, Deque<Long>> samplesByModelType = new ConcurrentHashMap<>();

    /**
     * Records one depth sample for every registered queue.
     */
    @Scheduled(fixedRateString = "${vader.backpressure.sample-interval-ms:15000}")
    public void sample() {
        for (var modelType : this.registry.names()) {
            var depth = this.registry.require(modelType).queuedRecordCount();
            var samples = this.samplesByModelType.computeIfAbsent(
                modelType, key -> new ArrayDeque<>(WINDOW));
            synchronized (samples) {
                samples.addLast(depth);
                while (samples.size() > WINDOW) {
                    samples.removeFirst();
                }
            }
            logger.debug("Sampled {} queue depth: {}", modelType, depth);
        }
    }

    /**
     * The most recent change in queued depth, expressed per minute. Zero until at least two
     * samples have been taken.
     *
     * @param modelType the queued model type
     * @return the rate of change per minute
     */
    public float ratePerMinuteFor(final String modelType) {
        var samples = this.samplesByModelType.get(modelType);
        if (samples == null) {
            return 0f;
        }
        synchronized (samples) {
            if (samples.size() < WINDOW) {
                return 0f;
            }
            var delta = samples.getLast() - samples.getFirst();
            return delta * (MILLIS_PER_MINUTE / this.sampleIntervalMs);
        }
    }
}
