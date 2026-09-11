package org.vader.core.server.service.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.model.vader.entity.ClientPromptOutboxMessageEntity;
import org.vader.common.model.vader.entity.OutboxMessageStatus;
import org.vader.core.server.repository.OutboxMessageRepository;

class AbstractInboxTest {

    @SuppressWarnings("unchecked")
    private final OutboxMessageRepository<ClientPromptOutboxMessageEntity> repository =
        mock(OutboxMessageRepository.class);

    private final QueueMessageProcessor processor = mock(QueueMessageProcessor.class);

    private TestInbox inbox;

    @BeforeEach
    void setUp() {
        this.inbox = new TestInbox(this.repository);
        ReflectionTestUtils.setField(this.inbox, "processor", this.processor);
        when(this.repository.countByStatus(OutboxMessageStatus.CLAIMED)).thenReturn(0L);
    }

    private static ClientPromptOutboxMessageEntity message() {
        return new ClientPromptOutboxMessageEntity();
    }

    private void claimReturns(final Optional<ClientPromptOutboxMessageEntity> first,
        final Optional<ClientPromptOutboxMessageEntity> second,
        final Optional<ClientPromptOutboxMessageEntity> third) {
        when(this.processor.claim(eq(this.repository), any()))
            .thenReturn(first, second, third);
    }

    @Test
    void drain_processesEveryPendingMessageThenStops() {
        var a = message();
        var b = message();
        claimReturns(Optional.of(a), Optional.of(b), Optional.empty());

        this.inbox.drain();

        assertThat(this.inbox.handled).containsExactly(a.getId(), b.getId());
        verify(this.processor).markProcessed(this.repository, a.getId());
        verify(this.processor).markProcessed(this.repository, b.getId());
        verify(this.processor, never()).markFailed(any(), any(), any());
    }

    @Test
    void drain_whenHandleThrows_marksTheMessageFailedAndContinues() {
        var a = message();
        var b = message();
        claimReturns(Optional.of(a), Optional.of(b), Optional.empty());
        this.inbox.failOn = a.getId();

        this.inbox.drain();

        verify(this.processor).markFailed(eq(this.repository), eq(a.getId()), contains("Boom"));
        verify(this.processor).markProcessed(this.repository, b.getId());
    }

    @Test
    void drain_doesNotClaimWhenAtTheConcurrencyCeiling() {
        when(this.repository.countByStatus(OutboxMessageStatus.CLAIMED)).thenReturn(1L);

        this.inbox.drain();

        verify(this.processor, never()).claim(any(), any());
    }

    @Test
    void drain_isNotReentrant() {
        var a = message();
        claimReturns(Optional.of(a), Optional.empty(), Optional.empty());
        this.inbox.reenterOnHandle = true;

        this.inbox.drain();

        // the re-entrant drain() from inside handle() is a no-op: message a is handled once,
        // and only the outer loop claims (a, then empty).
        assertThat(this.inbox.handled).containsExactly(a.getId());
        verify(this.processor, times(2)).claim(eq(this.repository), any());
    }

    private static final class TestInbox extends AbstractInbox<ClientPromptOutboxMessageEntity> {

        private final OutboxMessageRepository<ClientPromptOutboxMessageEntity> repository;
        private final List<String> handled = new ArrayList<>();
        private String failOn;
        private boolean reenterOnHandle;

        private TestInbox(final OutboxMessageRepository<ClientPromptOutboxMessageEntity> repo) {
            this.repository = repo;
        }

        @Override
        public String queuedModelType() {
            return "Test";
        }

        @Override
        public int maxOpenMessages() {
            return 1;
        }

        @Override
        protected OutboxMessageRepository<ClientPromptOutboxMessageEntity> repository() {
            return this.repository;
        }

        @Override
        protected void handle(final ClientPromptOutboxMessageEntity message) {
            this.handled.add(message.getId());
            if (this.reenterOnHandle) {
                this.drain();
            }
            if (message.getId().equals(this.failOn)) {
                throw new IllegalStateException("Boom");
            }
        }
    }
}
