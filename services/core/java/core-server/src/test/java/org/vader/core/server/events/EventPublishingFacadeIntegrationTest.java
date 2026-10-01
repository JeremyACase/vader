package org.vader.core.server.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.instancio.Instancio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs the facade against a real transaction manager and the default local strategy, publishing
 * an event no production listener handles so only the timing of delivery is under test.
 */
@SpringBootTest
@RecordApplicationEvents
@TestPropertySource(properties = "vader.scheduling.enabled=false")
class EventPublishingFacadeIntegrationTest {

    @Autowired
    private EventPublishingFacade facade;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ApplicationEvents applicationEvents;

    private TransactionTemplate transactionTemplate;
    private ProbeEvent event;

    @BeforeEach
    void setUp() {
        this.transactionTemplate = new TransactionTemplate(this.transactionManager);
        this.event = Instancio.create(ProbeEvent.class);
    }

    @Test
    void publish_withinCommittedTransaction_deliversOnlyAfterCommit() {
        this.transactionTemplate.executeWithoutResult(status -> {
            this.facade.publish(this.event);
            assertThat(this.recorded()).isEmpty();
        });

        assertThat(this.recorded()).containsExactly(this.event);
    }

    @Test
    void publish_withinRolledBackTransaction_neverDelivers() {
        this.transactionTemplate.executeWithoutResult(status -> {
            this.facade.publish(this.event);
            status.setRollbackOnly();
        });

        assertThat(this.recorded()).isEmpty();
    }

    @Test
    void publish_withoutTransaction_deliversImmediately() {
        this.facade.publish(this.event);

        assertThat(this.recorded()).containsExactly(this.event);
    }

    private List<ProbeEvent> recorded() {
        return this.applicationEvents.stream(ProbeEvent.class).toList();
    }

    record ProbeEvent(String id) {
    }
}
