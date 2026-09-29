package org.vader.core.server.taskagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.core.server.taskagent.model.ConversationMessage;
import org.vader.core.server.taskagent.model.ConversationRole;
import org.vader.core.server.taskagent.model.InferenceRequest;
import org.vader.core.server.taskagent.model.InferenceTurn;
import org.vader.core.server.taskagent.model.ToolCallInvocationRequest;
import org.vader.core.server.taskagent.model.ToolCallInvocationResult;

class AgentAssignmentControllerTest {

    private static final String ASSIGNMENT_ID = "aaaaaaaa-1111-2222-3333-444444444444";

    private InferenceTranscriptService inferenceTranscriptService;
    private TaskToolInvocationService toolInvocationService;
    private AgentAssignmentController controller;

    @BeforeEach
    void setUp() {
        this.inferenceTranscriptService = mock(InferenceTranscriptService.class);
        this.toolInvocationService = mock(TaskToolInvocationService.class);
        this.controller = new AgentAssignmentController();
        ReflectionTestUtils.setField(
            this.controller, "inferenceTranscriptService", this.inferenceTranscriptService);
        ReflectionTestUtils.setField(
            this.controller, "toolInvocationService", this.toolInvocationService);
    }

    @Test
    void inference_forwardsTheConversationAndReturnsTheTurn() {
        var messages = List.of(
            new ConversationMessage(ConversationRole.USER, "hi", null, null, null));
        var turn = new InferenceTurn("hello", List.of(), 5L, "stop");
        when(this.inferenceTranscriptService.recordInferenceTurn(ASSIGNMENT_ID, messages))
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
        when(this.toolInvocationService.invokeTool(
            ASSIGNMENT_ID, "call-1", "get_object_content", "{\"id\":\"abc\"}"))
            .thenReturn(result);

        var response = this.controller.invokeTool(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(result);
    }
}
