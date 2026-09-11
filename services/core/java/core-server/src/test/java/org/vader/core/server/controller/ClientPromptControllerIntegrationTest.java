package org.vader.core.server.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.model.vader.dto.ClientPrompt;
import org.vader.common.model.vader.entity.OutboxMessageStatus;
import org.vader.core.server.repository.ClientPromptOutboxMessageRepository;
import org.vader.core.server.repository.WorkflowRepository;
import org.vader.core.server.service.io.ClientPromptInbox;
import org.vader.core.server.service.strategies.inference.InterfaceInferenceGatewayStrategy;
import org.vader.core.server.service.strategies.orchestration.interfaces.InterfaceLlmOrchestrationStrategy;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "vader.scheduling.enabled=false")
class ClientPromptControllerIntegrationTest {

    private static final String VALID_PLAN = """
        {
          "objective": "Ship the onboarding flow",
          "taskGraph": {
            "tasks": [
              { "title": "Design", "description": "Design the onboarding screens" },
              { "title": "Build", "description": "Implement the onboarding screens" }
            ]
          }
        }
        """;

    @MockitoBean
    private InterfaceLlmOrchestrationStrategy orchestrator;

    @MockitoBean
    private InterfaceInferenceGatewayStrategy inferenceGateway;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClientPromptInbox clientPromptInbox;

    @Autowired
    private ClientPromptOutboxMessageRepository outboxMessageRepository;

    @Autowired
    private WorkflowRepository workflowRepository;

    private String postPrompt(final String text) throws Exception {
        var body = this.mockMvc.perform(multipart("/vader/core-server/client-prompt")
                .param("text", text))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.modelType").value("IngressResponse"))
            .andExpect(jsonPath("$.payloadModelType").value("ClientPrompt"))
            .andReturn().getResponse().getContentAsString();
        return this.objectMapper.readTree(body).get("id").asText();
    }

    @Test
    void postClientPrompt_enqueuesPendingMessageThatInboxDecomposes() throws Exception {
        when(this.orchestrator.orchestrate(any(ClientPrompt.class))).thenReturn(VALID_PLAN);

        var promptId = postPrompt("Help me ship onboarding");

        var message = this.outboxMessageRepository.findAll().stream()
            .filter(m -> m.getClientPrompt().getId().equals(promptId))
            .findFirst().orElseThrow();
        assertThat(message.getStatus()).isEqualTo(OutboxMessageStatus.PENDING);

        this.clientPromptInbox.drain();

        var settled = this.outboxMessageRepository.findById(message.getId()).orElseThrow();
        assertThat(settled.getStatus()).isEqualTo(OutboxMessageStatus.PROCESSED);
        assertThat(settled.getProcessedAt()).isNotNull();

        var workflow = this.workflowRepository.findAll().stream()
            .filter(w -> w.getClientPrompt().getId().equals(promptId))
            .findFirst().orElseThrow();
        assertThat(workflow.getTaskPlan().getObjective()).isEqualTo("Ship the onboarding flow");
        assertThat(workflow.getTaskPlan().getTaskGraph().getTasks()).hasSize(2);
    }

    @Test
    void postClientPrompt_whenLlmResponseFailsSchema_marksTheMessageFailedAndBuildsNoWorkflow()
        throws Exception {
        when(this.orchestrator.orchestrate(any(ClientPrompt.class)))
            .thenReturn("{\"objective\":\"no task graph\"}");

        var promptId = postPrompt("whatever");

        this.clientPromptInbox.drain();

        var message = this.outboxMessageRepository.findAll().stream()
            .filter(m -> m.getClientPrompt().getId().equals(promptId))
            .findFirst().orElseThrow();
        assertThat(message.getStatus()).isEqualTo(OutboxMessageStatus.FAILED);
        assertThat(message.getFailureReason()).contains("task-plan schema");

        assertThat(this.workflowRepository.findAll().stream()
            .anyMatch(w -> w.getClientPrompt().getId().equals(promptId))).isFalse();
    }

    @Test
    void postClientPrompt_withTooManyFiles_isRejectedWithFourHundred() throws Exception {
        var request = multipart("/vader/core-server/client-prompt");
        for (var i = 0; i < 6; i++) {
            request.file("files", ("file " + i).getBytes());
        }
        request.param("text", "too many");

        this.mockMvc.perform(request)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("validation_failed"));
    }
}
