package org.vader.core.server.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.library.implementation.service.mapper.ClientPromptDtoToEntityMapper;
import org.vader.common.library.implementation.service.mapper.TaskDtoMapper;
import org.vader.common.library.implementation.service.mapper.TaskGraphDtoMapper;
import org.vader.common.library.implementation.service.mapper.TaskPlanDtoMapper;
import org.vader.common.library.implementation.service.mapper.WorkflowDtoMapper;
import org.vader.common.model.vader.dto.ClientPrompt;
import org.vader.common.model.vader.dto.Workflow;
import org.vader.common.model.vader.entity.ClientPromptEntity;
import org.vader.common.model.vader.entity.WorkflowEntity;
import org.vader.core.server.orchestrator.OrchestratorResponseException;
import org.vader.core.server.service.WorkflowService;

class ClientPromptControllerTest {

    private WorkflowService workflowService;
    private ClientPromptController controller;

    @BeforeEach
    void setUp() {
        this.workflowService = mock(WorkflowService.class);
        this.controller = new ClientPromptController(
            this.workflowService,
            new ClientPromptDtoToEntityMapper(),
            workflowDtoMapper());
    }

    private static WorkflowDtoMapper workflowDtoMapper() {
        var taskGraphDtoMapper = new TaskGraphDtoMapper();
        ReflectionTestUtils.setField(taskGraphDtoMapper, "taskDtoMapper", new TaskDtoMapper());
        var taskPlanDtoMapper = new TaskPlanDtoMapper();
        ReflectionTestUtils.setField(taskPlanDtoMapper, "taskGraphDtoMapper", taskGraphDtoMapper);
        var workflowDtoMapper = new WorkflowDtoMapper();
        ReflectionTestUtils.setField(workflowDtoMapper, "taskPlanDtoMapper", taskPlanDtoMapper);
        return workflowDtoMapper;
    }

    private static ClientPrompt prompt(final String text) {
        var clientPrompt = new ClientPrompt();
        clientPrompt.setText(text);
        return clientPrompt;
    }

    @Test
    void receivePrompt_decomposesThePromptAndReturnsTheWorkflow() {
        var workflow = new WorkflowEntity();
        var clientPromptEntity = new ClientPromptEntity();
        workflow.setClientPrompt(clientPromptEntity);
        when(this.workflowService.decompose(any())).thenReturn(workflow);

        var response = this.controller.receivePrompt(prompt("What's the weather like?"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(Workflow.class);
    }

    @Test
    void receivePrompt_withAttachments_decomposesThePrompt() {
        var clientPrompt = prompt("Summarize the attached file.");
        clientPrompt.setFiles(List.of(new MockMultipartFile(
            "files", "notes.txt", "text/plain", "some content".getBytes())));
        when(this.workflowService.decompose(any())).thenReturn(new WorkflowEntity());

        var response = this.controller.receivePrompt(clientPrompt);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void handleOrchestratorResponse_returnsBadGatewayWithErrorBody() {
        var response = this.controller.handleOrchestratorResponse(
            new OrchestratorResponseException("Orchestrator returned an empty response."));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().error()).isEqualTo("orchestrator_response_invalid");
        assertThat(response.getBody().message()).contains("empty response");
    }
}
