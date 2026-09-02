package org.vader.core.server.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.model.vader.dto.ClientPrompt;
import org.vader.core.server.orchestrator.interfaces.InterfaceLlmOrchestrationStrategy;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
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

    @Autowired
    private MockMvc mockMvc;

    @Test
    void postClientPrompt_routesToTheLlmAndReturnsTheDecomposition() throws Exception {
        when(this.orchestrator.orchestrate(any(ClientPrompt.class))).thenReturn(VALID_PLAN);

        this.mockMvc.perform(multipart("/vader/core-server/client-prompt")
                .param("text", "Help me ship onboarding"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.clientPromptId").isNotEmpty())
            .andExpect(jsonPath("$.taskPlan.objective").value("Ship the onboarding flow"))
            .andExpect(jsonPath("$.taskPlan.taskGraph.tasks.length()").value(2))
            .andExpect(jsonPath("$.taskPlan.taskGraph.tasks[0].title").value("Design"));
    }

    @Test
    void postClientPrompt_whenLlmResponseFailsSchema_returns502WithErrorBody() throws Exception {
        when(this.orchestrator.orchestrate(any(ClientPrompt.class)))
            .thenReturn("{\"objective\":\"no task graph\"}");

        this.mockMvc.perform(multipart("/vader/core-server/client-prompt")
                .param("text", "whatever"))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.error").value("orchestrator_response_invalid"))
            .andExpect(jsonPath("$.message").isNotEmpty());
    }
}
