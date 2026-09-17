package org.vader.core.server.service.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.model.vader.entity.ClientPromptEntity;
import org.vader.common.model.vader.entity.ObjectMetadataEntity;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskAttemptStatus;
import org.vader.common.model.vader.entity.TaskAttemptToolCallEntity;
import org.vader.common.model.vader.entity.TaskAttemptTranscriptEntity;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.common.model.vader.entity.TaskGraphEntity;
import org.vader.common.model.vader.entity.TaskPlanEntity;
import org.vader.common.model.vader.entity.WorkflowEntity;
import org.vader.core.server.exceptions.UnknownToolException;
import org.vader.core.server.models.ConversationMessage;
import org.vader.core.server.models.ConversationRole;
import org.vader.core.server.models.InferenceToolCall;
import org.vader.core.server.models.InferenceTurn;
import org.vader.core.server.models.ResultRequest;
import org.vader.core.server.models.TaskAttemptSettledEvent;
import org.vader.core.server.repository.TaskAttemptRepository;
import org.vader.core.server.repository.TaskAttemptToolCallRepository;
import org.vader.core.server.repository.TaskAttemptTranscriptRepository;
import org.vader.core.server.service.registries.McpToolCallbackRegistry;
import org.vader.core.server.service.strategies.inference.InterfaceInferenceGatewayStrategy;

class TaskAttemptServiceTest {

    private static final String ATTEMPT_ID = "aaaaaaaa-1111-2222-3333-444444444444";
    private static final String WORKFLOW_ID = "wwwwwwww-1111-2222-3333-444444444444";

    private TaskAttemptRepository taskAttemptRepository;
    private TaskAttemptTranscriptRepository transcriptRepository;
    private TaskAttemptToolCallRepository toolCallRepository;
    private ApplicationEventPublisher eventPublisher;
    private InterfaceInferenceGatewayStrategy inferenceGateway;
    private McpToolCallbackRegistry toolCallbackRegistry;
    private TaskAttemptService service;

    @BeforeEach
    void setUp() {
        this.taskAttemptRepository = mock(TaskAttemptRepository.class);
        this.transcriptRepository = mock(TaskAttemptTranscriptRepository.class);
        this.toolCallRepository = mock(TaskAttemptToolCallRepository.class);
        this.eventPublisher = mock(ApplicationEventPublisher.class);
        this.inferenceGateway = mock(InterfaceInferenceGatewayStrategy.class);
        this.toolCallbackRegistry = mock(McpToolCallbackRegistry.class);

        this.service = new TaskAttemptService();
        ReflectionTestUtils.setField(
            this.service, "taskAttemptRepository", this.taskAttemptRepository);
        ReflectionTestUtils.setField(
            this.service, "transcriptRepository", this.transcriptRepository);
        ReflectionTestUtils.setField(
            this.service, "toolCallRepository", this.toolCallRepository);
        ReflectionTestUtils.setField(this.service, "eventPublisher", this.eventPublisher);
        ReflectionTestUtils.setField(this.service, "inferenceGateway", this.inferenceGateway);
        ReflectionTestUtils.setField(
            this.service, "toolCallbackRegistry", this.toolCallbackRegistry);
        ReflectionTestUtils.setField(this.service, "objectMapper", new ObjectMapper());
    }

    private static TaskAttemptEntity attemptInWorkflow(final String workflowId) {
        var clientPrompt = new ClientPromptEntity();
        clientPrompt.setText("Plan a birthday party.");
        var workflow = new WorkflowEntity();
        workflow.setId(workflowId);
        workflow.setClientPrompt(clientPrompt);
        var taskPlan = new TaskPlanEntity();
        taskPlan.setWorkflow(workflow);
        var taskGraph = new TaskGraphEntity();
        taskGraph.setTaskPlan(taskPlan);
        var task = new TaskEntity();
        task.setTaskGraph(taskGraph);

        var attempt = new TaskAttemptEntity();
        attempt.setId(ATTEMPT_ID);
        attempt.setTask(task);
        attempt.setStatus(TaskAttemptStatus.RUNNING);
        return attempt;
    }

    @Test
    void fetchAssignment_contextIncludesTheOriginalClientPromptText() {
        var attempt = attemptInWorkflow(WORKFLOW_ID);
        attempt.getTask().getTaskGraph().getTaskPlan().getWorkflow().getClientPrompt()
            .setText("Analyze the uploaded spreadsheet and write a report.");
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));

        var response = this.service.fetchAssignment(ATTEMPT_ID);

        assertThat(response.context())
            .contains("Analyze the uploaded spreadsheet and write a report.");
    }

    @Test
    void fetchAssignment_contextListsFilesAttachedToTheOriginalRequest() {
        var attempt = attemptInWorkflow(WORKFLOW_ID);
        var clientPrompt =
            attempt.getTask().getTaskGraph().getTaskPlan().getWorkflow().getClientPrompt();
        var file = new ObjectMetadataEntity();
        file.setOriginalFilename("sales.csv");
        file.setContentType("text/csv");
        clientPrompt.setFiles(Set.of(file));
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));

        var response = this.service.fetchAssignment(ATTEMPT_ID);

        assertThat(response.context())
            .contains("sales.csv")
            .contains("get_object_content");
    }

    @Test
    void fetchAssignment_contextIncludesPrerequisiteTaskResults() {
        var attempt = attemptInWorkflow(WORKFLOW_ID);
        var dependency = new TaskEntity();
        dependency.setId("dep-task-id");
        dependency.setTitle("Draft the report");
        attempt.getTask().setDependsOn(Set.of(dependency));
        var dependencyAttempt = new TaskAttemptEntity();
        dependencyAttempt.setResult("Here is the draft report...");
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        when(this.taskAttemptRepository.findFirstByTaskIdOrderByAttemptNumberDesc("dep-task-id"))
            .thenReturn(Optional.of(dependencyAttempt));

        var response = this.service.fetchAssignment(ATTEMPT_ID);

        assertThat(response.context())
            .contains("Draft the report")
            .contains("Here is the draft report...");
    }

    @Test
    void fetchAssignment_withNoFilesOrDependencies_contextIsJustTheOriginalRequest() {
        var attempt = attemptInWorkflow(WORKFLOW_ID);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));

        var response = this.service.fetchAssignment(ATTEMPT_ID);

        assertThat(response.context())
            .doesNotContain("Files attached")
            .doesNotContain("prerequisite tasks");
    }

    @Test
    void submitResult_publishesSettlementWithTheWorkflowAndAssignmentIds() {
        var attempt = attemptInWorkflow(WORKFLOW_ID);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));

        this.service.submitResult(
            ATTEMPT_ID, new ResultRequest(TaskAttemptStatus.SUCCEEDED, "done", null));

        var eventCaptor = ArgumentCaptor.forClass(TaskAttemptSettledEvent.class);
        verify(this.eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().workflowId()).isEqualTo(WORKFLOW_ID);
        assertThat(eventCaptor.getValue().assignmentId()).isEqualTo(ATTEMPT_ID);
        assertThat(attempt.getStatus()).isEqualTo(TaskAttemptStatus.SUCCEEDED);
    }

    @Test
    void markDispatchFailed_alsoPublishesSettlementForTheFailedAssignment() {
        var attempt = attemptInWorkflow(WORKFLOW_ID);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));

        this.service.markDispatchFailed(ATTEMPT_ID, "could not create Job");

        var eventCaptor = ArgumentCaptor.forClass(TaskAttemptSettledEvent.class);
        verify(this.eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().assignmentId()).isEqualTo(ATTEMPT_ID);
        assertThat(attempt.getStatus()).isEqualTo(TaskAttemptStatus.FAILED);
    }

    @Test
    void recordInferenceTurn_returnsTheTurnAndPersistsTranscriptEntry() {
        var attempt = attemptInWorkflow(WORKFLOW_ID);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        var messages = List.of(
            new ConversationMessage(ConversationRole.USER, "hi", null, null, null));
        var turn = new InferenceTurn("hello", List.of(), 5L);
        when(this.inferenceGateway.complete(messages)).thenReturn(turn);

        var result = this.service.recordInferenceTurn(ATTEMPT_ID, messages);

        assertThat(result).isSameAs(turn);
        var transcriptCaptor = ArgumentCaptor.forClass(TaskAttemptTranscriptEntity.class);
        verify(this.transcriptRepository).save(transcriptCaptor.capture());
        assertThat(transcriptCaptor.getValue().getResponse()).isEqualTo("hello");
        assertThat(transcriptCaptor.getValue().getPrompt()).contains("hi");
        assertThat(transcriptCaptor.getValue().getTokensSpent()).isEqualTo(5L);
    }

    @Test
    void recordInferenceTurn_whenTheTurnIsPureToolCalls_persistsThemAsTheResponse() {
        var attempt = attemptInWorkflow(WORKFLOW_ID);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        var messages = List.of(
            new ConversationMessage(ConversationRole.USER, "analyze the file", null, null, null));
        var toolCalls = List.of(new InferenceToolCall("call-1", "get_object_content", "{}"));
        when(this.inferenceGateway.complete(messages))
            .thenReturn(new InferenceTurn(null, toolCalls, 5L));

        this.service.recordInferenceTurn(ATTEMPT_ID, messages);

        var transcriptCaptor = ArgumentCaptor.forClass(TaskAttemptTranscriptEntity.class);
        verify(this.transcriptRepository).save(transcriptCaptor.capture());
        assertThat(transcriptCaptor.getValue().getResponse()).contains("get_object_content");
    }

    @Test
    void invokeTool_delegatesToTheRegisteredCallbackAndReturnsItsResult() {
        var attempt = attemptInWorkflow(WORKFLOW_ID);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        var toolCallback = mock(ToolCallback.class);
        when(toolCallback.call("{\"id\":\"abc\"}")).thenReturn("{\"content\":\"...\"}");
        when(this.toolCallbackRegistry.findByName("get_object_content"))
            .thenReturn(Optional.of(toolCallback));

        var result = this.service.invokeTool(
            ATTEMPT_ID, "call-1", "get_object_content", "{\"id\":\"abc\"}");

        assertThat(result.toolCallId()).isEqualTo("call-1");
        assertThat(result.resultJson()).isEqualTo("{\"content\":\"...\"}");
    }

    @Test
    void invokeTool_persistsAnImmutableAuditRowBeforeReturning() {
        var attempt = attemptInWorkflow(WORKFLOW_ID);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        var toolCallback = mock(ToolCallback.class);
        when(toolCallback.call("{\"id\":\"abc\"}")).thenReturn("{\"content\":\"...\"}");
        when(this.toolCallbackRegistry.findByName("get_object_content"))
            .thenReturn(Optional.of(toolCallback));

        this.service.invokeTool(ATTEMPT_ID, "call-1", "get_object_content", "{\"id\":\"abc\"}");

        var recordCaptor = ArgumentCaptor.forClass(TaskAttemptToolCallEntity.class);
        verify(this.toolCallRepository).save(recordCaptor.capture());
        var record = recordCaptor.getValue();
        assertThat(record.getTaskAttempt()).isSameAs(attempt);
        assertThat(record.getToolCallId()).isEqualTo("call-1");
        assertThat(record.getToolName()).isEqualTo("get_object_content");
        assertThat(record.getArgumentsJson()).isEqualTo("{\"id\":\"abc\"}");
        assertThat(record.getResultJson()).isEqualTo("{\"content\":\"...\"}");
    }

    @Test
    void invokeTool_forAnUnregisteredToolName_throwsUnknownTool() {
        var attempt = attemptInWorkflow(WORKFLOW_ID);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        when(this.toolCallbackRegistry.findByName("bogus_tool")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> this.service.invokeTool(
                ATTEMPT_ID, "call-1", "bogus_tool", "{}"))
            .isInstanceOf(UnknownToolException.class)
            .hasMessageContaining("bogus_tool");
    }

    @Test
    void invokeTool_forAnUnregisteredToolName_stillPersistsTheFailedAttempt() {
        var attempt = attemptInWorkflow(WORKFLOW_ID);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        when(this.toolCallbackRegistry.findByName("bogus_tool")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> this.service.invokeTool(
                ATTEMPT_ID, "call-1", "bogus_tool", "{}"))
            .isInstanceOf(UnknownToolException.class);

        var recordCaptor = ArgumentCaptor.forClass(TaskAttemptToolCallEntity.class);
        verify(this.toolCallRepository).save(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getToolName()).isEqualTo("bogus_tool");
        assertThat(recordCaptor.getValue().getResultJson()).contains("bogus_tool");
    }
}
