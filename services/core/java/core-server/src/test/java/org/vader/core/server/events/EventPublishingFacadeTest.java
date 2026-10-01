package org.vader.core.server.events;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.instancio.Instancio;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.vader.core.server.events.interfaces.InterfaceEventPublishingStrategy;
import org.vader.core.server.workflow.TaskAttemptSettledEvent;

class EventPublishingFacadeTest {

    private InterfaceEventPublishingStrategy strategy;
    private EventPublishingFacade facade;
    private TaskAttemptSettledEvent event;

    @BeforeEach
    void setUp() {
        this.strategy = mock(InterfaceEventPublishingStrategy.class);
        this.facade = new EventPublishingFacade();
        ReflectionTestUtils.setField(this.facade, "strategy", this.strategy);
        this.event = Instancio.create(TaskAttemptSettledEvent.class);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void publish_withoutTransaction_deliversImmediately() {
        this.facade.publish(this.event);

        verify(this.strategy).publish(this.event);
    }

    @Test
    void publish_withinTransaction_waitsForCommit() {
        TransactionSynchronizationManager.initSynchronization();

        this.facade.publish(this.event);

        verify(this.strategy, never()).publish(this.event);
        TransactionSynchronizationManager.getSynchronizations()
            .forEach(TransactionSynchronization::afterCommit);
        verify(this.strategy).publish(this.event);
    }

    @Test
    void publish_withinRolledBackTransaction_neverDelivers() {
        TransactionSynchronizationManager.initSynchronization();

        this.facade.publish(this.event);

        TransactionSynchronizationManager.getSynchronizations().forEach(synchronization ->
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(this.strategy, never()).publish(this.event);
    }

    @Test
    void publish_whenDeliveryFails_doesNotThrowAtTheCaller() {
        doThrow(new IllegalStateException("listener exploded")).when(this.strategy)
            .publish(this.event);

        assertThatCode(() -> this.facade.publish(this.event)).doesNotThrowAnyException();
    }
}
