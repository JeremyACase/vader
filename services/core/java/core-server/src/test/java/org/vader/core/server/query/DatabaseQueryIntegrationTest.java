package org.vader.core.server.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
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
import org.vader.core.server.intake.ClientPromptInbox;
import org.vader.core.server.llm.LlmRequestQueue;
import org.vader.core.server.orchestration.LlmTaskPlanAdapter;
import org.vader.core.server.orchestration.TaskPlanRefinementLlmExecutor;
import org.vader.core.server.orchestration.model.TaskPlanRefinementVerdict;
import org.vader.core.server.query.model.EntityDescription;

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
    private LlmTaskPlanAdapter taskPlanAdapter;

    @MockitoBean
    private LlmRequestQueue requestQueue;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DatabaseQueryService databaseQueryService;

    @Autowired
    private ClientPromptInbox clientPromptInbox;

    @BeforeEach
    void stubRefinementApproval() {
        // Not the focus of these tests -- always approve so decomposition behaves exactly as it
        // did before refinement existed.
        when(this.requestQueue.submit(eq(TaskPlanRefinementLlmExecutor.class), any()))
            .thenReturn(new TaskPlanRefinementVerdict(false, "approved"));
    }

    private void submitPrompt() throws Exception {
        when(this.taskPlanAdapter.decompose(any(ClientPrompt.class), any(), any()))
            .thenReturn(PLAN);
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
