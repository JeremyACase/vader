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
import org.vader.common.model.vader.entity.TaskAttemptStatus;
import org.vader.core.server.models.ConversationMessage;
import org.vader.core.server.models.ConversationRole;
import org.vader.core.server.models.DecompositionRequest;
import org.vader.core.server.models.EvaluationRequest;
import org.vader.core.server.models.EvaluationVerdict;
import org.vader.core.server.models.InferenceTurn;
import org.vader.core.server.models.OutboxMessageEnqueuedEvent;
import org.vader.core.server.models.ReattemptDecision;
import org.vader.core.server.models.ReattemptDecisionRequest;
import org.vader.core.server.repository.LlmRequestOutboxMessageRepository;
import org.vader.core.server.service.agent.orchestrator.strategies.LlmTaskPlan;

class LlmRequestInboxTest {

    private LlmRequestOutboxMessageRepository messageRepository;
    private InferenceTurnLlmExecutor inferenceTurnExecutor;
    private DecompositionLlmExecutor decompositionExecutor;
    private EvaluationLlmExecutor evaluationExecutor;
    private ReattemptDecisionLlmExecutor reattemptDecisionExecutor;
    private TaskExecutor executor;
    private LlmRequestInbox inbox;

    @BeforeEach
    void setUp() {
        this.messageRepository = mock(LlmRequestOutboxMessageRepository.class);
        this.inferenceTurnExecutor = mock(InferenceTurnLlmExecutor.class);
        this.decompositionExecutor = mock(DecompositionLlmExecutor.class);
        this.evaluationExecutor = mock(EvaluationLlmExecutor.class);
        this.reattemptDecisionExecutor = mock(ReattemptDecisionLlmExecutor.class);
        this.executor = mock(TaskExecutor.class);

        this.inbox = new LlmRequestInbox();
        ReflectionTestUtils.setField(this.inbox, "messageRepository", this.messageRepository);
        ReflectionTestUtils.setField(this.inbox, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(
            this.inbox, "inferenceTurnExecutor", this.inferenceTurnExecutor);
        ReflectionTestUtils.setField(
            this.inbox, "decompositionExecutor", this.decompositionExecutor);
        ReflectionTestUtils.setField(
            this.inbox, "evaluationExecutor", this.evaluationExecutor);
        ReflectionTestUtils.setField(
            this.inbox, "reattemptDecisionExecutor", this.reattemptDecisionExecutor);
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
        var turn = new InferenceTurn("hello", List.of(), 5L, "stop");
        when(this.inferenceTurnExecutor.execute(messages)).thenReturn(turn);

        ReflectionTestUtils.invokeMethod(this.inbox, "handle", message);

        assertThat(message.getResponseJson()).contains("hello").contains("5");
        verify(this.messageRepository).save(message);
    }

    @Test
    void handle_forDecomposition_executesAndStoresTheSerializedOutcome() {
        var message = new LlmRequestOutboxMessageEntity();
        message.setKind(LlmRequestKind.DECOMPOSITION);
        message.setRequestJson(
            "{\"clientPromptText\":\"plan a birthday party\",\"revisionGuidance\":null}");
        var plan = new LlmTaskPlan("reasoning", "throw a party",
            List.of(new LlmTaskPlan.LlmTask("book venue", "find a place", List.of())));
        when(this.decompositionExecutor.execute(
            new DecompositionRequest("plan a birthday party", null)))
            .thenReturn(new DecompositionOutcome(plan, null));

        ReflectionTestUtils.invokeMethod(this.inbox, "handle", message);

        assertThat(message.getResponseJson()).contains("throw a party");
        verify(this.messageRepository).save(message);
    }

    @Test
    void handle_forEvaluation_executesAndStoresTheSerializedOutcome() throws Exception {
        var message = new LlmRequestOutboxMessageEntity();
        message.setKind(LlmRequestKind.EVALUATION);
        var request = new EvaluationRequest(
            "title", "description", TaskAttemptStatus.SUCCEEDED, "result", null, List.of());
        message.setRequestJson(new ObjectMapper().writeValueAsString(request));
        when(this.evaluationExecutor.execute(request)).thenReturn(
            new EvaluationOutcome(new EvaluationVerdict(true, "looks right"), null));

        ReflectionTestUtils.invokeMethod(this.inbox, "handle", message);

        assertThat(message.getResponseJson()).contains("looks right");
        verify(this.messageRepository).save(message);
    }

    @Test
    void handle_forReattemptDecision_executesAndStoresTheSerializedOutcome() throws Exception {
        var message = new LlmRequestOutboxMessageEntity();
        message.setKind(LlmRequestKind.REATTEMPT_DECISION);
        var request = new ReattemptDecisionRequest(
            "title", "description", 1, 3, "it broke", List.of());
        message.setRequestJson(new ObjectMapper().writeValueAsString(request));
        when(this.reattemptDecisionExecutor.execute(request)).thenReturn(
            new ReattemptDecisionOutcome(new ReattemptDecision(true, "worth it"), null));

        ReflectionTestUtils.invokeMethod(this.inbox, "handle", message);

        assertThat(message.getResponseJson()).contains("worth it");
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
