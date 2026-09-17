package org.vader.core.server.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.core.server.exceptions.UnknownToolException;
import org.vader.core.server.models.ConversationMessage;
import org.vader.core.server.models.ConversationRole;
import org.vader.core.server.models.InferenceRequest;
import org.vader.core.server.models.InferenceTurn;
import org.vader.core.server.models.ToolCallInvocationRequest;
import org.vader.core.server.models.ToolCallInvocationResult;
import org.vader.core.server.service.agent.TaskAttemptService;

class AgentAssignmentControllerTest {

    private static final String ASSIGNMENT_ID = "aaaaaaaa-1111-2222-3333-444444444444";

    private TaskAttemptService taskAttemptService;
    private AgentAssignmentController controller;

    @BeforeEach
    void setUp() {
        this.taskAttemptService = mock(TaskAttemptService.class);
        this.controller = new AgentAssignmentController();
        ReflectionTestUtils.setField(
            this.controller, "taskAttemptService", this.taskAttemptService);
    }

    @Test
    void inference_forwardsTheConversationAndReturnsTheTurn() {
        var messages = List.of(
            new ConversationMessage(ConversationRole.USER, "hi", null, null, null));
        var turn = new InferenceTurn("hello", List.of(), 5L);
        when(this.taskAttemptService.recordInferenceTurn(ASSIGNMENT_ID, messages))
            .thenReturn(turn);

        var response = this.controller.inference(new InferenceRequest(ASSIGNMENT_ID, messages));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(turn);
    }

    @Test
    void invokeTool_forwardsTheCallAndReturnsTheResult() {
        var request = new ToolCallInvocationRequest(
            ASSIGNMENT_ID, "call-1", "get_object_content", "{\"id\":\"abc\"}");
        var result = new ToolCallInvocationResult("call-1", "{\"content\":\"...\"}");
        when(this.taskAttemptService.invokeTool(
            ASSIGNMENT_ID, "call-1", "get_object_content", "{\"id\":\"abc\"}"))
            .thenReturn(result);

        var response = this.controller.invokeTool(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(result);
    }

    @Test
    void handleUnknownTool_returnsBadRequestWithStableErrorCode() {
        var response = this.controller.handleUnknownTool(
            new UnknownToolException("No tool registered with name 'bogus_tool'"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("error")).isEqualTo("unknown_tool");
        assertThat(response.getBody().get("message")).contains("bogus_tool");
    }
}
