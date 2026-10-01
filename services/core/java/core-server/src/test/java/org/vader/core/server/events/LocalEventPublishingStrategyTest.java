package org.vader.core.server.events;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.instancio.Instancio;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.core.server.workflow.WorkflowDecomposedEvent;

class LocalEventPublishingStrategyTest {

    @Test
    void publish_handsTheEventToThisReplicasListeners() {
        var applicationEventPublisher = mock(ApplicationEventPublisher.class);
        var strategy = new LocalEventPublishingStrategy();
        ReflectionTestUtils.setField(
            strategy, "applicationEventPublisher", applicationEventPublisher);
        var event = Instancio.create(WorkflowDecomposedEvent.class);

        strategy.publish(event);

        verify(applicationEventPublisher).publishEvent(event);
    }
}
