package org.vader.core.server.messaging;

import jakarta.annotation.PostConstruct;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Indexes every {@link AbstractQueueInspectionAdapter} by its queue name, so the inspection
 * endpoint can resolve a queue by name. Mirrors {@code BackpressureRegistry}.
 */
@Service
public class QueueInspectionRegistry {

    @Autowired
    private List<AbstractQueueInspectionAdapter<?, ?>> adapters;

    private final Map<String, AbstractQueueInspectionAdapter<?, ?>> byName = new LinkedHashMap<>();

    @PostConstruct
    void index() {
        for (var adapter : this.adapters) {
            this.byName.put(adapter.queueName(), adapter);
        }
    }

    /**
     * Returns every inspectable queue.
     *
     * @return the adapters, one per queue
     */
    public Collection<AbstractQueueInspectionAdapter<?, ?>> all() {
        return this.byName.values();
    }

    /**
     * Resolves a queue by name.
     *
     * @param name the queue name (case-sensitive)
     * @return the queue's adapter
     * @throws IllegalArgumentException if no queue is registered under {@code name}
     */
    public AbstractQueueInspectionAdapter<?, ?> require(final String name) {
        var adapter = this.byName.get(name);
        if (adapter == null) {
            throw new IllegalArgumentException(
                "No inbox/outbox queue named '" + name + "'. Queues: " + this.byName.keySet());
        }
        return adapter;
    }
}
