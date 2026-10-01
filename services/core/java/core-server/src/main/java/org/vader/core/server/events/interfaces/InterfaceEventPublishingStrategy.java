package org.vader.core.server.events.interfaces;

import org.vader.core.server.events.EventPublishingFacade;

/**
 * Strategy for delivering a committed domain event to its listeners, hiding how far it travels.
 *
 * <p>Active implementation is selected at startup via {@code vader.events.type}. The local
 * strategy is the default and delivers in-process, to this replica's listeners only, which is
 * correct for a single-replica deployment. A broker-backed strategy would reach every replica.
 * Either way an event arrives as an ordinary Spring application event, so listeners are plain
 * {@code @EventListener} methods and never know which strategy is active.</p>
 *
 * <p>Callers go through {@link EventPublishingFacade}, which decides when an event goes out; a
 * strategy only delivers it.</p>
 */
public interface InterfaceEventPublishingStrategy {

    /**
     * Delivers one event to its listeners.
     *
     * @param event the event, a record whose fields identify what happened (ids, not entities)
     */
    void publish(Object event);
}
