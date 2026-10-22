package org.vader.core.server.taskagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.model.vader.entity.TaskAttemptStatus;
import org.vader.core.server.sandbox.TaskAttemptOutputCollectionService;
import org.vader.core.server.taskagent.model.ConversationMessage;
import org.vader.core.server.taskagent.model.ConversationRole;
import org.vader.core.server.taskagent.model.InferenceRequest;
import org.vader.core.server.taskagent.model.InferenceTurn;
import org.vader.core.server.taskagent.model.ResultRequest;
import org.vader.core.server.taskagent.model.ToolCallInvocationRequest;
import org.vader.core.server.taskagent.model.ToolCallInvocationResult;
import org.vader.core.server.taskagent.model.TurnUsage;

class AgentAssignmentControllerTest {

    private static final TurnUsage USAGE = new TurnUsage(3L, 2L, 40L);

    private static final String ASSIGNMENT_ID = "aaaaaaaa-1111-2222-3333-444444444444";

    private InferenceTranscriptService inferenceTranscriptService;
    private TaskToolInvocationService toolInvocationService;
    private TaskAttemptLifecycleService lifecycleService;
    private TaskAttemptOutputCollectionService outputCollectionService;
    private AgentAssignmentController controller;

    @BeforeEach
    void setUp() {
        this.inferenceTranscriptService = mock(InferenceTranscriptService.class);
        this.toolInvocationService = mock(TaskToolInvocationService.class);
        this.lifecycleService = mock(TaskAttemptLifecycleService.class);
        this.outputCollectionService = mock(TaskAttemptOutputCollectionService.class);
        this.controller = new AgentAssignmentController();
        ReflectionTestUtils.setField(this.controller, "lifecycleService", this.lifecycleService);
        ReflectionTestUtils.setField(
            this.controller, "outputCollectionService", this.outputCollectionService);
        ReflectionTestUtils.setField(
            this.controller, "inferenceTranscriptService", this.inferenceTranscriptService);
        ReflectionTestUtils.setField(
            this.controller, "toolInvocationService", this.toolInvocationService);
    }

    @Test
    void inference_forwardsTheConversationAndReturnsTheTurn() {
        var messages = List.of(
            new ConversationMessage(ConversationRole.USER, "hi", null, null, null));
        var turn = new InferenceTurn("hello", List.of(), 5L, "stop", USAGE);
        when(this.inferenceTranscriptService.recordInferenceTurn(ASSIGNMENT_ID, messages))
            .thenReturn(turn);

        var response = this.controller.inference(new InferenceRequest(ASSIGNMENT_ID, messages));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(turn);
    }

    @Test
    void submitResult_savesSucceedingAttemptsFilesBeforeSettlingThem() {
        var request = new ResultRequest(TaskAttemptStatus.SUCCEEDED, "done", null);

        var response = this.controller.submitResult(ASSIGNMENT_ID, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        var order = inOrder(this.outputCollectionService, this.lifecycleService);
        order.verify(this.outputCollectionService).collect(ASSIGNMENT_ID);
        order.verify(this.lifecycleService).submitResult(ASSIGNMENT_ID, request);
    }

    @Test
    void submitResult_savesNoFilesForFailedAttempts() {
        var request = new ResultRequest(TaskAttemptStatus.FAILED, null, "cut off");

        this.controller.submitResult(ASSIGNMENT_ID, request);

        verifyNoInteractions(this.outputCollectionService);
    }

    @Test
    void submitResult_withoutTheSandboxOperator_settlesWithoutSavingFiles() {
        ReflectionTestUtils.setField(this.controller, "outputCollectionService", null);
        var request = new ResultRequest(TaskAttemptStatus.SUCCEEDED, "done", null);

        var response = this.controller.submitResult(ASSIGNMENT_ID, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        inOrder(this.lifecycleService).verify(this.lifecycleService)
            .submitResult(ASSIGNMENT_ID, request);
    }

    @Test
    void invokeTool_forwardsTheCallAndReturnsTheResult() {
        var request = new ToolCallInvocationRequest(
            ASSIGNMENT_ID, "call-1", "get_object_content", "{\"id\":\"abc\"}");
        var result = new ToolCallInvocationResult("call-1", "{\"content\":\"...\"}");
        when(this.toolInvocationService.invokeTool(
            ASSIGNMENT_ID, "call-1", "get_object_content", "{\"id\":\"abc\"}"))
            .thenReturn(result);

        var response = this.controller.invokeTool(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(result);
    }
}
