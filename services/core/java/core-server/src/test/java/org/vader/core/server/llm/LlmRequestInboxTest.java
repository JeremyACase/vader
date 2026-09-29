package org.vader.core.server.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.vader.common.model.vader.entity.LlmRequestKind;
import org.vader.common.model.vader.entity.LlmRequestOutboxMessageEntity;
import org.vader.common.model.vader.entity.TaskAttemptStatus;
import org.vader.core.server.messaging.OutboxMessageEnqueuedEvent;
import org.vader.core.server.review.EvaluationLlmExecutor;
import org.vader.core.server.review.model.EvaluationRequest;
import org.vader.core.server.review.model.EvaluationVerdict;
import org.vader.core.server.taskagent.InferenceTurnLlmExecutor;
import org.vader.core.server.taskagent.model.ConversationMessage;
import org.vader.core.server.taskagent.model.ConversationRole;
import org.vader.core.server.taskagent.model.InferenceTurn;

class LlmRequestInboxTest {

    private static final EvaluationRequest EVALUATION_REQUEST = new EvaluationRequest(
        "title", "description", TaskAttemptStatus.SUCCEEDED, "result", null, List.of(), null);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private LlmRequestOutboxMessageRepository messageRepository;
    private InferenceTurnLlmExecutor inferenceTurnExecutor;
    private EvaluationLlmExecutor evaluationExecutor;
    private TaskExecutor executor;
    private LlmRequestInbox inbox;

    @BeforeEach
    void setUp() {
        this.messageRepository = mock(LlmRequestOutboxMessageRepository.class);
        this.inferenceTurnExecutor = mock(InferenceTurnLlmExecutor.class);
        this.evaluationExecutor = mock(EvaluationLlmExecutor.class);
        this.executor = mock(TaskExecutor.class);
        var executorRegistry = mock(LlmExecutorRegistry.class);
        doReturn(this.inferenceTurnExecutor)
            .when(executorRegistry).forKind(LlmRequestKind.INFERENCE_TURN);
        doReturn(this.evaluationExecutor).when(executorRegistry).forKind(LlmRequestKind.EVALUATION);

        this.inbox = new LlmRequestInbox();
        ReflectionTestUtils.setField(this.inbox, "messageRepository", this.messageRepository);
        ReflectionTestUtils.setField(this.inbox, "objectMapper", this.objectMapper);
        ReflectionTestUtils.setField(this.inbox, "executorRegistry", executorRegistry);
        ReflectionTestUtils.setField(this.inbox, "executor", this.executor);
        when(this.messageRepository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    private LlmRequestOutboxMessageEntity messageOf(final LlmRequestKind kind, final Object request)
            throws Exception {
        var message = new LlmRequestOutboxMessageEntity();
        message.setKind(kind);
        message.setRequestJson(this.objectMapper.writeValueAsString(request));
        return message;
    }

    private LlmResponseEnvelope envelopeOf(final LlmRequestOutboxMessageEntity message)
            throws Exception {
        return this.objectMapper.readValue(message.getResponseJson(), LlmResponseEnvelope.class);
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
    void handle_decodesGenericRequestTypesAndStoresTheResponse() throws Exception {
        var messages = List.of(
            new ConversationMessage(ConversationRole.USER, "hi", null, null, null));
        var message = this.messageOf(LlmRequestKind.INFERENCE_TURN, messages);
        when(this.inferenceTurnExecutor.execute(messages))
            .thenReturn(new InferenceTurn("hello", List.of(), 5L, "stop"));

        ReflectionTestUtils.invokeMethod(this.inbox, "handle", message);

        var envelope = this.envelopeOf(message);
        assertThat(envelope.isUnreachable()).isFalse();
        assertThat(this.objectMapper.treeToValue(envelope.value(), InferenceTurn.class))
            .isEqualTo(new InferenceTurn("hello", List.of(), 5L, "stop"));
        verify(this.messageRepository).save(message);
    }

    @Test
    void handle_dispatchesToTheExecutorForTheMessagesKind() throws Exception {
        var message = this.messageOf(LlmRequestKind.EVALUATION, EVALUATION_REQUEST);
        when(this.evaluationExecutor.execute(EVALUATION_REQUEST))
            .thenReturn(new EvaluationVerdict(true, "looks right"));

        ReflectionTestUtils.invokeMethod(this.inbox, "handle", message);

        var verdict = this.objectMapper.treeToValue(
            this.envelopeOf(message).value(), EvaluationVerdict.class);
        assertThat(verdict).isEqualTo(new EvaluationVerdict(true, "looks right"));
    }

    @Test
    void handle_whenTheLlmIsUnreachable_storesAnUnreachableEnvelopeInsteadOfThrowing()
            throws Exception {
        var message = this.messageOf(LlmRequestKind.EVALUATION, EVALUATION_REQUEST);
        when(this.evaluationExecutor.execute(EVALUATION_REQUEST))
            .thenThrow(new ResourceAccessException("connection refused"));

        ReflectionTestUtils.invokeMethod(this.inbox, "handle", message);

        var envelope = this.envelopeOf(message);
        assertThat(envelope.isUnreachable()).isTrue();
        assertThat(envelope.unreachableReason()).contains("connection refused");
        verify(this.messageRepository).save(message);
    }

    @Test
    void handle_whenTheLlmFailsTransiently_storesAnUnreachableEnvelope() throws Exception {
        var message = this.messageOf(LlmRequestKind.EVALUATION, EVALUATION_REQUEST);
        when(this.evaluationExecutor.execute(EVALUATION_REQUEST))
            .thenThrow(new TransientAiException("503 from Ollama"));

        ReflectionTestUtils.invokeMethod(this.inbox, "handle", message);

        assertThat(this.envelopeOf(message).unreachableReason()).contains("503 from Ollama");
    }

    @Test
    void handle_whenTheExecutorFailsOtherwise_propagatesSoTheMessageSettlesFailed()
            throws Exception {
        var message = this.messageOf(LlmRequestKind.EVALUATION, EVALUATION_REQUEST);
        when(this.evaluationExecutor.execute(EVALUATION_REQUEST))
            .thenThrow(new IllegalStateException("unparseable output"));

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(this.inbox, "handle", message))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("unparseable output");
        verify(this.messageRepository, never()).save(message);
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
