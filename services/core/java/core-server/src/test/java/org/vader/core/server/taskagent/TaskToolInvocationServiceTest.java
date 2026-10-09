package org.vader.core.server.taskagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.vader.core.server.mcp.AgentToolAudience.TASK_EXECUTION;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.vader.common.model.vader.entity.TaskAttemptToolCallEntity;
import org.vader.core.server.mcp.McpToolCallbackRegistry;
import org.vader.core.server.workflow.TaskAttemptRepository;
import tools.jackson.databind.ObjectMapper;

class TaskToolInvocationServiceTest {

    private static final String ATTEMPT_ID = TaskAttemptObjectMother.ATTEMPT_ID;
    private static final String WORKFLOW_ID = "wwwwwwww-1111-2222-3333-444444444444";

    private TaskAttemptRepository taskAttemptRepository;
    private TaskAttemptToolCallRepository toolCallRepository;
    private McpToolCallbackRegistry toolCallbackRegistry;
    private PlatformTransactionManager transactionManager;
    private TaskAttemptLifecycleService lifecycleService;
    private TaskToolInvocationService service;

    @BeforeEach
    void setUp() {
        this.taskAttemptRepository = mock(TaskAttemptRepository.class);
        this.toolCallRepository = mock(TaskAttemptToolCallRepository.class);
        this.toolCallbackRegistry = mock(McpToolCallbackRegistry.class);
        this.transactionManager = mock(PlatformTransactionManager.class);
        this.lifecycleService = new TaskAttemptLifecycleService();
        ReflectionTestUtils.setField(
            this.lifecycleService, "taskAttemptRepository", this.taskAttemptRepository);

        this.service = new TaskToolInvocationService();
        ReflectionTestUtils.setField(this.service, "lifecycleService", this.lifecycleService);
        ReflectionTestUtils.setField(
            this.service, "toolCallbackRegistry", this.toolCallbackRegistry);
        ReflectionTestUtils.setField(
            this.service, "taskAttemptRepository", this.taskAttemptRepository);
        ReflectionTestUtils.setField(this.service, "toolCallRepository", this.toolCallRepository);
        ReflectionTestUtils.setField(this.service, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(
            this.service, "transactionManager", this.transactionManager);
        this.service.init();
    }

    @Test
    void invokeTool_runsTheToolBetweenItsTwoShortTransactionsRatherThanInsideOne() {
        var attempt = TaskAttemptObjectMother.attemptInWorkflow(WORKFLOW_ID);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        var toolCallback = mock(ToolCallback.class);
        when(toolCallback.call(anyString(), any(ToolContext.class))).thenReturn("{}");
        when(this.toolCallbackRegistry.findByName(TASK_EXECUTION, "run_python_code"))
            .thenReturn(Optional.of(toolCallback));

        this.service.invokeTool(ATTEMPT_ID, "call-1", "run_python_code", "{\"code\":\"pass\"}");

        var order = inOrder(this.transactionManager, toolCallback, this.toolCallRepository);
        order.verify(this.transactionManager).getTransaction(any());
        order.verify(this.transactionManager).commit(any());
        order.verify(toolCallback).call(anyString(), any(ToolContext.class));
        order.verify(this.transactionManager).getTransaction(any());
        order.verify(this.toolCallRepository).save(any());
        order.verify(this.transactionManager).commit(any());
    }

    @Test
    void invokeTool_delegatesToTheRegisteredCallbackAndReturnsItsResult() {
        var attempt = TaskAttemptObjectMother.attemptInWorkflow(WORKFLOW_ID);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        var toolCallback = mock(ToolCallback.class);
        when(toolCallback.call(eq("{\"id\":\"abc\"}"), any(ToolContext.class)))
            .thenReturn("{\"content\":\"...\"}");
        when(this.toolCallbackRegistry.findByName(TASK_EXECUTION, "get_object_content"))
            .thenReturn(Optional.of(toolCallback));

        var result = this.service.invokeTool(
            ATTEMPT_ID, "call-1", "get_object_content", "{\"id\":\"abc\"}");

        assertThat(result.toolCallId()).isEqualTo("call-1");
        assertThat(result.resultJson()).isEqualTo("{\"content\":\"...\"}");
    }

    @Test
    void invokeTool_passesTheCallingAttemptsOwnIdAsServerSuppliedToolContext() {
        var attempt = TaskAttemptObjectMother.attemptInWorkflow(WORKFLOW_ID);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        var toolCallback = mock(ToolCallback.class);
        when(toolCallback.call(anyString(), any(ToolContext.class))).thenReturn("{}");
        when(this.toolCallbackRegistry.findByName(TASK_EXECUTION, "run_python_code"))
            .thenReturn(Optional.of(toolCallback));

        this.service.invokeTool(ATTEMPT_ID, "call-1", "run_python_code", "{\"code\":\"pass\"}");

        var contextCaptor = ArgumentCaptor.forClass(ToolContext.class);
        verify(toolCallback).call(eq("{\"code\":\"pass\"}"), contextCaptor.capture());
        assertThat(TaskAttemptToolContext.taskAttemptIdFrom(contextCaptor.getValue()))
            .isEqualTo(ATTEMPT_ID);
    }

    @Test
    void invokeTool_persistsAnImmutableAuditRowBeforeReturning() {
        var attempt = TaskAttemptObjectMother.attemptInWorkflow(WORKFLOW_ID);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        var toolCallback = mock(ToolCallback.class);
        when(toolCallback.call(eq("{\"id\":\"abc\"}"), any(ToolContext.class)))
            .thenReturn("{\"content\":\"...\"}");
        when(this.toolCallbackRegistry.findByName(TASK_EXECUTION, "get_object_content"))
            .thenReturn(Optional.of(toolCallback));
        when(this.taskAttemptRepository.getReferenceById(ATTEMPT_ID)).thenReturn(attempt);

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
    void invokeTool_whenTheRegisteredToolThrows_returnsGracefulErrorInstead() {
        var attempt = TaskAttemptObjectMother.attemptInWorkflow(WORKFLOW_ID);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        var toolCallback = mock(ToolCallback.class);
        when(toolCallback.call(eq("{\"id\":\"abc\"}"), any(ToolContext.class)))
            .thenThrow(new IllegalStateException("sandbox pod unreachable"));
        when(this.toolCallbackRegistry.findByName(TASK_EXECUTION, "stage_object"))
            .thenReturn(Optional.of(toolCallback));

        var result = this.service.invokeTool(
            ATTEMPT_ID, "call-1", "stage_object", "{\"id\":\"abc\"}");

        assertThat(result.toolCallId()).isEqualTo("call-1");
        assertThat(result.resultJson())
            .contains("error")
            .contains("sandbox pod unreachable");
    }

    @Test
    void invokeTool_whenTheRegisteredToolThrows_stillPersistsTheAuditRow() {
        var attempt = TaskAttemptObjectMother.attemptInWorkflow(WORKFLOW_ID);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        var toolCallback = mock(ToolCallback.class);
        when(toolCallback.call(eq("{\"id\":\"abc\"}"), any(ToolContext.class)))
            .thenThrow(new IllegalStateException("sandbox pod unreachable"));
        when(this.toolCallbackRegistry.findByName(TASK_EXECUTION, "stage_object"))
            .thenReturn(Optional.of(toolCallback));

        this.service.invokeTool(ATTEMPT_ID, "call-1", "stage_object", "{\"id\":\"abc\"}");

        var recordCaptor = ArgumentCaptor.forClass(TaskAttemptToolCallEntity.class);
        verify(this.toolCallRepository).save(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getResultJson())
            .contains("error")
            .contains("sandbox pod unreachable");
    }

    @Test
    void invokeTool_forAnUnregisteredToolName_throwsUnknownTool() {
        var attempt = TaskAttemptObjectMother.attemptInWorkflow(WORKFLOW_ID);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        when(this.toolCallbackRegistry.findByName(TASK_EXECUTION, "bogus_tool"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> this.service.invokeTool(
                ATTEMPT_ID, "call-1", "bogus_tool", "{}"))
            .isInstanceOf(UnknownToolException.class)
            .hasMessageContaining("bogus_tool");
    }

    @Test
    void invokeTool_forPostTaskUpdate_overwritesTheModelSuppliedTaskAndAttemptIdWithTheOwnOnes() {
        var attempt = TaskAttemptObjectMother.attemptInWorkflow(WORKFLOW_ID);
        attempt.getTask().setId("real-task-id");
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        var toolCallback = mock(ToolCallback.class);
        when(this.toolCallbackRegistry.findByName(TASK_EXECUTION, "post_task_update"))
            .thenReturn(Optional.of(toolCallback));
        when(toolCallback.call(anyString(), any(ToolContext.class)))
            .thenReturn("Recorded update against task real-task-id.");

        this.service.invokeTool(
            ATTEMPT_ID, "call-1", "post_task_update",
            "{\"taskId\":\"some-other-task\",\"taskAttemptId\":\"some-other-attempt\","
                + "\"description\":\"note\"}");

        var argumentsCaptor = ArgumentCaptor.forClass(String.class);
        verify(toolCallback).call(argumentsCaptor.capture(), any(ToolContext.class));
        assertThat(argumentsCaptor.getValue())
            .contains("\"taskId\":\"real-task-id\"")
            .contains("\"taskAttemptId\":\"" + ATTEMPT_ID + "\"")
            .doesNotContain("some-other-task")
            .doesNotContain("some-other-attempt");
    }

    @Test
    void invokeTool_forAnUnregisteredToolName_stillPersistsTheFailedAttempt() {
        var attempt = TaskAttemptObjectMother.attemptInWorkflow(WORKFLOW_ID);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        when(this.toolCallbackRegistry.findByName(TASK_EXECUTION, "bogus_tool"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> this.service.invokeTool(
                ATTEMPT_ID, "call-1", "bogus_tool", "{}"))
            .isInstanceOf(UnknownToolException.class);

        var recordCaptor = ArgumentCaptor.forClass(TaskAttemptToolCallEntity.class);
        verify(this.toolCallRepository).save(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getToolName()).isEqualTo("bogus_tool");
        assertThat(recordCaptor.getValue().getResultJson()).contains("bogus_tool");
    }
}
