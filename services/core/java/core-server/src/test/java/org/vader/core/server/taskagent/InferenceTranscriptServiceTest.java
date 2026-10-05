package org.vader.core.server.taskagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.vader.common.model.vader.entity.TaskAttemptTranscriptEntity;
import org.vader.core.server.taskagent.model.ConversationMessage;
import org.vader.core.server.taskagent.model.ConversationRole;
import org.vader.core.server.taskagent.model.InferenceToolCall;
import org.vader.core.server.taskagent.model.InferenceTurn;
import org.vader.core.server.workflow.TaskAttemptRepository;

class InferenceTranscriptServiceTest {

    private static final String ATTEMPT_ID = TaskAttemptObjectMother.ATTEMPT_ID;
    private static final String WORKFLOW_ID = "wwwwwwww-1111-2222-3333-444444444444";

    private TaskAttemptRepository taskAttemptRepository;
    private TaskAttemptTranscriptRepository transcriptRepository;
    private InferenceGateway inferenceGateway;
    private PlatformTransactionManager transactionManager;
    private TaskAttemptLifecycleService lifecycleService;
    private InferenceTranscriptService service;

    @BeforeEach
    void setUp() {
        this.taskAttemptRepository = mock(TaskAttemptRepository.class);
        this.transcriptRepository = mock(TaskAttemptTranscriptRepository.class);
        this.inferenceGateway = mock(InferenceGateway.class);
        this.transactionManager = mock(PlatformTransactionManager.class);
        this.lifecycleService = new TaskAttemptLifecycleService();
        ReflectionTestUtils.setField(
            this.lifecycleService, "taskAttemptRepository", this.taskAttemptRepository);

        this.service = new InferenceTranscriptService();
        ReflectionTestUtils.setField(this.service, "lifecycleService", this.lifecycleService);
        ReflectionTestUtils.setField(this.service, "inferenceGateway", this.inferenceGateway);
        ReflectionTestUtils.setField(
            this.service, "transcriptRepository", this.transcriptRepository);
        ReflectionTestUtils.setField(this.service, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(
            this.service, "taskAttemptRepository", this.taskAttemptRepository);
        ReflectionTestUtils.setField(
            this.service, "transactionManager", this.transactionManager);
        this.service.init();
    }

    @Test
    void recordInferenceTurn_opensTheTranscriptTransactionOnlyAfterTheLlmCallReturns() {
        var attempt = TaskAttemptObjectMother.attemptInWorkflow(WORKFLOW_ID);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        var messages = List.of(
            new ConversationMessage(ConversationRole.USER, "hi", null, null, null));
        when(this.inferenceGateway.complete(messages))
            .thenReturn(new InferenceTurn("hello", List.of(), 5L, "stop"));

        this.service.recordInferenceTurn(ATTEMPT_ID, messages);

        var order = inOrder(this.inferenceGateway, this.transactionManager,
            this.transcriptRepository);
        order.verify(this.inferenceGateway).complete(messages);
        order.verify(this.transactionManager).getTransaction(any());
        order.verify(this.transcriptRepository).save(any());
        order.verify(this.transactionManager).commit(any());
    }

    @Test
    void recordInferenceTurn_returnsTheTurnAndPersistsTranscriptEntry() {
        var attempt = TaskAttemptObjectMother.attemptInWorkflow(WORKFLOW_ID);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        var messages = List.of(
            new ConversationMessage(ConversationRole.USER, "hi", null, null, null));
        var turn = new InferenceTurn("hello", List.of(), 5L, "stop");
        when(this.inferenceGateway.complete(messages)).thenReturn(turn);

        var result = this.service.recordInferenceTurn(ATTEMPT_ID, messages);

        assertThat(result).isSameAs(turn);
        var transcriptCaptor = ArgumentCaptor.forClass(TaskAttemptTranscriptEntity.class);
        verify(this.transcriptRepository).save(transcriptCaptor.capture());
        assertThat(transcriptCaptor.getValue().getResponse()).isEqualTo("hello");
        assertThat(transcriptCaptor.getValue().getPrompt()).contains("hi");
        assertThat(transcriptCaptor.getValue().getMessageCount()).isEqualTo(1);
        assertThat(transcriptCaptor.getValue().getTokensSpent()).isEqualTo(5L);
        assertThat(transcriptCaptor.getValue().getFinishReason()).isEqualTo("stop");
    }

    @Test
    void recordInferenceTurn_onLaterTurns_persistsOnlyTheMessagesAddedSinceLastTime() {
        var attempt = TaskAttemptObjectMother.attemptInWorkflow(WORKFLOW_ID);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        var previousTranscript = new TaskAttemptTranscriptEntity();
        previousTranscript.setMessageCount(2);
        when(this.transcriptRepository.findFirstByTaskAttemptIdOrderByTurnIndexDesc(ATTEMPT_ID))
            .thenReturn(Optional.of(previousTranscript));
        var messages = List.of(
            new ConversationMessage(ConversationRole.SYSTEM, "instructions", null, null, null),
            new ConversationMessage(ConversationRole.USER, "analyze the file", null, null, null),
            new ConversationMessage(
                ConversationRole.ASSISTANT, null,
                List.of(new InferenceToolCall("call-1", "stage_object", "{}")), null, null),
            new ConversationMessage(
                ConversationRole.TOOL, "{\"filename\":\"a.csv\"}", null, "call-1",
                "stage_object"));
        when(this.inferenceGateway.complete(messages))
            .thenReturn(new InferenceTurn("done", List.of(), 5L, "stop"));

        this.service.recordInferenceTurn(ATTEMPT_ID, messages);

        var transcriptCaptor = ArgumentCaptor.forClass(TaskAttemptTranscriptEntity.class);
        verify(this.transcriptRepository).save(transcriptCaptor.capture());
        var savedPrompt = transcriptCaptor.getValue().getPrompt();
        assertThat(savedPrompt)
            .contains("stage_object")
            .doesNotContain("instructions")
            .doesNotContain("analyze the file");
        assertThat(transcriptCaptor.getValue().getMessageCount()).isEqualTo(4);
    }

    @Test
    void recordInferenceTurn_whenTheTurnIsPureToolCalls_persistsThemAsTheResponse() {
        var attempt = TaskAttemptObjectMother.attemptInWorkflow(WORKFLOW_ID);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        var messages = List.of(
            new ConversationMessage(ConversationRole.USER, "analyze the file", null, null, null));
        var toolCalls = List.of(new InferenceToolCall("call-1", "get_object_content", "{}"));
        when(this.inferenceGateway.complete(messages))
            .thenReturn(new InferenceTurn(null, toolCalls, 5L, "stop"));

        this.service.recordInferenceTurn(ATTEMPT_ID, messages);

        var transcriptCaptor = ArgumentCaptor.forClass(TaskAttemptTranscriptEntity.class);
        verify(this.transcriptRepository).save(transcriptCaptor.capture());
        assertThat(transcriptCaptor.getValue().getResponse()).contains("get_object_content");
    }

    @Test
    void recordInferenceTurn_whenToolCallsComeWithEmptyContent_stillPersistsTheToolCalls() {
        // Ollama's shape for a tool-call turn: content is "" rather than null.
        var attempt = TaskAttemptObjectMother.attemptInWorkflow(WORKFLOW_ID);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        var messages = List.of(
            new ConversationMessage(ConversationRole.USER, "analyze the file", null, null, null));
        var toolCalls = List.of(new InferenceToolCall("", "run_python_code", "{\"code\":\"1\"}"));
        when(this.inferenceGateway.complete(messages))
            .thenReturn(new InferenceTurn("", toolCalls, 5L, "stop"));

        this.service.recordInferenceTurn(ATTEMPT_ID, messages);

        var transcriptCaptor = ArgumentCaptor.forClass(TaskAttemptTranscriptEntity.class);
        verify(this.transcriptRepository).save(transcriptCaptor.capture());
        assertThat(transcriptCaptor.getValue().getResponse()).contains("run_python_code");
    }

    @Test
    void recordInferenceTurn_whenTheTurnHasBothTextAndToolCalls_persistsBoth() {
        var attempt = TaskAttemptObjectMother.attemptInWorkflow(WORKFLOW_ID);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        var messages = List.of(
            new ConversationMessage(ConversationRole.USER, "analyze the file", null, null, null));
        var toolCalls = List.of(new InferenceToolCall("", "run_python_code", "{}"));
        when(this.inferenceGateway.complete(messages))
            .thenReturn(new InferenceTurn("Let me look at the sheets.", toolCalls, 5L, "stop"));

        this.service.recordInferenceTurn(ATTEMPT_ID, messages);

        var transcriptCaptor = ArgumentCaptor.forClass(TaskAttemptTranscriptEntity.class);
        verify(this.transcriptRepository).save(transcriptCaptor.capture());
        assertThat(transcriptCaptor.getValue().getResponse())
            .contains("Let me look at the sheets.")
            .contains("run_python_code");
    }

}
