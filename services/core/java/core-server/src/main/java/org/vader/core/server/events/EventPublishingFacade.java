package org.vader.core.server.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.vader.core.server.events.interfaces.InterfaceEventPublishingStrategy;

/**
 * The one entry point for announcing a domain event. Callers hand over the event and never learn
 * how it travels; the active {@link InterfaceEventPublishingStrategy} delivers it.
 *
 * <p>Owns what every strategy shares. An event raised inside a transaction goes out only after
 * that transaction commits, so a rolled-back write never announces anything; one raised outside a
 * transaction goes out at once. Events are hints that let a consumer act before its next
 * scheduled poll -- the database stays the source of truth -- so a delivery that fails is logged
 * rather than thrown at a caller whose work has already committed.</p>
 *
 * <p>Delivery after commit still runs on the committing thread, whose {@code EntityManager} is
 * still bound, so a listener that needs a transaction of its own must hand off to an executor
 * rather than call a {@code @Transactional} method directly.</p>
 */
@Service
public class EventPublishingFacade {

    private static final Logger logger = LoggerFactory.getLogger(EventPublishingFacade.class);

    @Autowired
    private InterfaceEventPublishingStrategy strategy;

    /**
     * Publishes an event once the current transaction commits, or immediately if there is none.
     *
     * @param event the event, a record whose fields identify what happened (ids, not entities)
     */
    public void publish(final Object event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                new AfterCommitDelivery(event));
        } else {
            this.deliver(event);
        }
    }

    private void deliver(final Object event) {
        try {
            this.strategy.publish(event);
            logger.debug("Published {}", event);
        } catch (RuntimeException e) {
            logger.warn("Could not publish {}: {}", event, e.getMessage(), e);
        }
    }

    /** Delivers one event when, and only if, the transaction it was raised in commits. */
    private final class AfterCommitDelivery implements TransactionSynchronization {

        private final Object event;

        private AfterCommitDelivery(final Object event) {
            this.event = event;
        }

        @Override
        public void afterCommit() {
            EventPublishingFacade.this.deliver(this.event);
        }
    }
}
