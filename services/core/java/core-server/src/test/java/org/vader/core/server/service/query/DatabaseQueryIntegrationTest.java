package org.vader.core.server.service.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.library.dao.model.QueryFilter;
import org.vader.common.library.dao.model.QueryFilterParameter;
import org.vader.common.library.dao.model.QueryOperatorType;
import org.vader.common.model.vader.dto.ClientPrompt;
import org.vader.core.server.models.EntityDescription;
import org.vader.core.server.service.agent.evaluator.strategies.interfaces.InterfaceEvaluatorStrategy;
import org.vader.core.server.service.agent.orchestrator.strategies.interfaces.InterfaceLlmOrchestrationStrategy;
import org.vader.core.server.service.agent.orchestrator.strategies.interfaces.InterfaceReattemptDecisionStrategy;
import org.vader.core.server.service.io.ClientPromptInbox;
import org.vader.core.server.service.strategies.inference.InterfaceInferenceGatewayStrategy;
import org.vader.core.server.service.strategies.synthesis.interfaces.InterfaceWorkflowSynthesisStrategy;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "vader.scheduling.enabled=false")
class DatabaseQueryIntegrationTest {

    private static final String PLAN = """
        {"objective":"Plan and run a small birthday party for a friend.",
         "taskGraph":{"tasks":[
           {"title":"Arrange food and cake","description":"Order a cake."},
           {"title":"Handle the venue","description":"Prepare the space."}]}}
        """;

    @MockitoBean
    private InterfaceLlmOrchestrationStrategy orchestrator;

    @MockitoBean
    private InterfaceInferenceGatewayStrategy inferenceGateway;

    @MockitoBean
    private InterfaceWorkflowSynthesisStrategy synthesisStrategy;

    @MockitoBean
    private InterfaceEvaluatorStrategy evaluatorStrategy;

    @MockitoBean
    private InterfaceReattemptDecisionStrategy reattemptDecisionStrategy;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DatabaseQueryService databaseQueryService;

    @Autowired
    private ClientPromptInbox clientPromptInbox;

    private void submitPrompt() throws Exception {
        when(this.orchestrator.orchestrate(any(ClientPrompt.class))).thenReturn(PLAN);
        this.mockMvc.perform(multipart("/vader/core-server/client-prompt")
                .param("text", "Plan a birthday party"))
            .andExpect(status().isAccepted());
        this.clientPromptInbox.drain();
    }

    @Test
    void workflowIsQueryableByNestedObjective_overRest() throws Exception {
        this.submitPrompt();

        this.mockMvc.perform(post("/vader/core-server/data/workflow/query")
                .contentType("application/json")
                .content("{\"parameters\":[{\"key\":\"taskPlan.objective\",\"operator\":\"LIKE\","
                    + "\"value\":\"%party%\"}],\"page\":0,\"pageSize\":10}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].taskPlan.objective").value(
                "Plan and run a small birthday party for a friend."));
    }

    @Test
    void tasksAreQueryableByTitle_overTheService() throws Exception {
        this.submitPrompt();

        var filter = new QueryFilter();
        filter.setParameters(List.of(parameter("title", QueryOperatorType.LIKE, "%cake%")));

        var result = this.databaseQueryService.query("Task", filter);

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content()).singleElement()
            .extracting(dto -> ((org.vader.common.model.vader.dto.Task) dto).getTitle())
            .isEqualTo("Arrange food and cake");
    }

    @Test
    void describe_listsTheTenEntitiesAndNotFileContent() {
        assertThat(this.databaseQueryService.describe())
            .extracting(EntityDescription::name)
            .containsExactlyInAnyOrder(
                "Workflow", "ClientPrompt", "TaskPlan", "TaskGraph", "Task", "ObjectMetadata",
                "TaskAttempt", "TaskAttemptTranscript", "TaskAttemptToolCall", "TaskUpdate");
    }

    private static QueryFilterParameter parameter(
        final String key, final QueryOperatorType operator, final String value) {
        var parameter = new QueryFilterParameter();
        parameter.setKey(key);
        parameter.setOperator(operator);
        parameter.setValue(value);
        return parameter;
    }
}
