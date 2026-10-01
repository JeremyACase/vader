package org.vader.core.server.events;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.vader.core.server.events.interfaces.InterfaceEventPublishingStrategy;

/**
 * Delivers events in-process, straight to this replica's listeners.
 *
 * <p>Active when {@code vader.events.type} is {@code local}, or when the property is absent
 * (i.e. {@code matchIfMissing = true} makes this the default). Correct only for a single-replica
 * deployment: another replica never hears these events, and falls back on its scheduled polls.</p>
 */
@Component
@ConditionalOnProperty(
    prefix = "vader.events",
    name = "type",
    havingValue = "local",
    matchIfMissing = true)
public class LocalEventPublishingStrategy implements InterfaceEventPublishingStrategy {

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publish(final Object event) {
        this.applicationEventPublisher.publishEvent(event);
    }
}
