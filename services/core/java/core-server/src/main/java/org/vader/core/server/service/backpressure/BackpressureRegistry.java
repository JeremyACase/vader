package org.vader.core.server.service.backpressure;

import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Indexes every inbox/outbox queue by the payload {@code modelType} it carries, so the back
 * pressure endpoint can resolve a queue by name. Mirrors {@code VaderDaoRegistry}.
 */
@Component
public class BackpressureRegistry {

    @Autowired
    private List<InterfaceQueueBackpressure> queues;

    private final Map<String, InterfaceQueueBackpressure> byModelType = new LinkedHashMap<>();

    @PostConstruct
    void index() {
        for (var queue : this.queues) {
            this.byModelType.put(queue.queuedModelType(), queue);
        }
    }

    /**
     * Returns every model type that has an inbox/outbox queue.
     *
     * @return the queued model-type names
     */
    public Set<String> names() {
        return this.byModelType.keySet();
    }

    /**
     * Resolves the queue for a model type.
     *
     * @param modelType the payload model type (case-sensitive)
     * @return the queue
     * @throws IllegalArgumentException if no queue is registered for {@code modelType}
     */
    public InterfaceQueueBackpressure require(final String modelType) {
        var queue = this.byModelType.get(modelType);
        if (queue == null) {
            throw new IllegalArgumentException(
                "No inbox/outbox queue for '" + modelType + "'. Queued model types: "
                    + this.byModelType.keySet());
        }
        return queue;
    }
}
