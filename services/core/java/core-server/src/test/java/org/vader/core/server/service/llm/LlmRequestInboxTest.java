package org.vader.core.server.service.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.model.vader.entity.LlmRequestKind;
import org.vader.common.model.vader.entity.LlmRequestOutboxMessageEntity;
import org.vader.core.server.models.ConversationMessage;
import org.vader.core.server.models.ConversationRole;
import org.vader.core.server.models.InferenceTurn;
import org.vader.core.server.models.OutboxMessageEnqueuedEvent;
import org.vader.core.server.repository.LlmRequestOutboxMessageRepository;
import org.vader.core.server.service.strategies.orchestration.LlmTaskPlan;

class LlmRequestInboxTest {

    private LlmRequestOutboxMessageRepository messageRepository;
    private InferenceTurnLlmExecutor inferenceTurnExecutor;
    private DecompositionLlmExecutor decompositionExecutor;
    private TaskExecutor executor;
    private LlmRequestInbox inbox;

    @BeforeEach
    void setUp() {
        this.messageRepository = mock(LlmRequestOutboxMessageRepository.class);
        this.inferenceTurnExecutor = mock(InferenceTurnLlmExecutor.class);
        this.decompositionExecutor = mock(DecompositionLlmExecutor.class);
        this.executor = mock(TaskExecutor.class);

        this.inbox = new LlmRequestInbox();
        ReflectionTestUtils.setField(this.inbox, "messageRepository", this.messageRepository);
        ReflectionTestUtils.setField(this.inbox, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(
            this.inbox, "inferenceTurnExecutor", this.inferenceTurnExecutor);
        ReflectionTestUtils.setField(
            this.inbox, "decompositionExecutor", this.decompositionExecutor);
        ReflectionTestUtils.setField(this.inbox, "executor", this.executor);
        when(this.messageRepository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void queuedModelType_isLlmRequest() {
        assertThat(this.inbox.queuedModelType()).isEqualTo("LlmRequest");
    }

    @Test
    void maxOpenMessages_isAlwaysOne() {
        assertThat(this.inbox.maxOpenMessages()).isEqualTo(1);
    }

    @Test
    void handle_forAnInferenceTurn_executesAndStoresTheSerializedResponse() throws Exception {
        var messages = List.of(
            new ConversationMessage(ConversationRole.USER, "hi", null, null, null));
        var message = new LlmRequestOutboxMessageEntity();
        message.setKind(LlmRequestKind.INFERENCE_TURN);
        message.setRequestJson(new ObjectMapper().writeValueAsString(messages));
        var turn = new InferenceTurn("hello", List.of(), 5L);
        when(this.inferenceTurnExecutor.execute(messages)).thenReturn(turn);

        ReflectionTestUtils.invokeMethod(this.inbox, "handle", message);

        assertThat(message.getResponseJson()).contains("hello").contains("5");
        verify(this.messageRepository).save(message);
    }

    @Test
    void handle_forDecomposition_executesAndStoresTheSerializedOutcome() {
        var message = new LlmRequestOutboxMessageEntity();
        message.setKind(LlmRequestKind.DECOMPOSITION);
        message.setRequestJson("plan a birthday party");
        var plan = new LlmTaskPlan("reasoning", "throw a party",
            List.of(new LlmTaskPlan.LlmTask("book venue", "find a place")));
        when(this.decompositionExecutor.execute("plan a birthday party"))
            .thenReturn(new DecompositionOutcome(plan, null));

        ReflectionTestUtils.invokeMethod(this.inbox, "handle", message);

        assertThat(message.getResponseJson()).contains("throw a party");
        verify(this.messageRepository).save(message);
    }

    @Test
    void onEnqueued_forMatchingEvent_triggersDrainOnTheDedicatedExecutor() {
        this.inbox.onEnqueued(new OutboxMessageEnqueuedEvent("LlmRequest"));

        verify(this.executor).execute(any());
    }

    @Test
    void onEnqueued_forAnUnrelatedEvent_doesNothing() {
        this.inbox.onEnqueued(new OutboxMessageEnqueuedEvent("ClientPrompt"));

        verify(this.executor, never()).execute(any());
    }
}
