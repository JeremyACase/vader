package org.vader.core.server.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.model.vader.dto.ClientPrompt;
import org.vader.common.model.vader.entity.ClientPromptEntity;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.core.server.intake.ClientPromptRepository;
import org.vader.core.server.llm.LlmRequestQueue;
import org.vader.core.server.orchestration.model.TaskPlanRefinementVerdict;
import org.vader.core.server.workflow.TaskPlanRepository;
import org.vader.core.server.workflow.WorkflowRepository;

@SpringBootTest
@Transactional
@TestPropertySource(properties = "vader.scheduling.enabled=false")
class OrchestratorAgentServiceIntegrationTest {

    private static final String VALID_PLAN = """
        {
          "objective": "Ship the onboarding flow",
          "taskGraph": {
            "tasks": [
              {
                "id": "11111111-1111-1111-1111-111111111111",
                "title": "Design",
                "description": "Design the onboarding screens",
                "subTasks": [
                  { "title": "Wireframe", "description": "Low-fidelity wireframes" }
                ]
              },
              {
                "id": "22222222-2222-2222-2222-222222222222",
                "title": "Build",
                "description": "Implement the onboarding screens",
                "dependsOnTaskIds": ["11111111-1111-1111-1111-111111111111"]
              }
            ]
          }
        }
        """;

    @MockitoBean
    private LlmTaskPlanAdapter taskPlanAdapter;

    @MockitoBean
    private LlmRequestQueue requestQueue;

    @Autowired
    private OrchestratorAgentService orchestratorAgentService;

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private TaskPlanRepository taskPlanRepository;

    @Autowired
    private ClientPromptRepository clientPromptRepository;

    @BeforeEach
    void stubRefinementApproval() {
        // Not the focus of these tests -- always approve so decompose() behaves exactly as it
        // did before refinement existed, unless a test overrides this stub itself.
        when(this.requestQueue.submit(eq(TaskPlanRefinementLlmExecutor.class), any()))
            .thenReturn(new TaskPlanRefinementVerdict(false, "approved"));
    }

    private ClientPromptEntity persistedPrompt(final String text) {
        var prompt = new ClientPromptEntity();
        prompt.setText(text);
        return this.clientPromptRepository.save(prompt);
    }

    @Test
    void decompose_persistsTheDecompositionUnderWorkflowAndLinksThePlanBackToIt() {
        when(this.taskPlanAdapter.decompose(any(ClientPrompt.class), any())).thenReturn(VALID_PLAN);

        var saved = this.orchestratorAgentService.decompose(
            persistedPrompt("Help me ship onboarding").getId());

        var workflow = this.workflowRepository.findById(saved.getId()).orElseThrow();
        assertThat(workflow.getClientPrompt()).isNotNull();
        assertThat(workflow.getClientPrompt().getText()).isEqualTo("Help me ship onboarding");

        var taskPlan = workflow.getTaskPlan();
        assertThat(taskPlan).isNotNull();
        assertThat(taskPlan.getObjective()).isEqualTo("Ship the onboarding flow");
        assertThat(taskPlan.getWorkflow().getId()).isEqualTo(workflow.getId());

        var reloadedPlan = this.taskPlanRepository.findById(taskPlan.getId()).orElseThrow();
        assertThat(reloadedPlan.getWorkflow().getId()).isEqualTo(workflow.getId());

        var graph = taskPlan.getTaskGraph();
        assertThat(graph).isNotNull();
        assertThat(graph.getTasks()).extracting(TaskEntity::getTitle)
            .containsExactlyInAnyOrder("Design", "Build");

        var design = graph.getTasks().stream()
            .filter(task -> task.getTitle().equals("Design")).findFirst().orElseThrow();
        assertThat(design.getSubTasks())
            .extracting(TaskEntity::getTitle).containsExactly("Wireframe");

        var build = graph.getTasks().stream()
            .filter(task -> task.getTitle().equals("Build")).findFirst().orElseThrow();
        assertThat(build.getDependsOn()).extracting(TaskEntity::getTitle).containsExactly("Design");
    }

    @Test
    void decompose_whenResponseFailsSchema_throwsAndPersistsNoWorkflow() {
        when(this.taskPlanAdapter.decompose(any(ClientPrompt.class), any()))
            .thenReturn("{\"objective\":\"no task graph here\"}");

        var promptId = persistedPrompt("whatever").getId();
        assertThatThrownBy(() -> this.orchestratorAgentService.decompose(promptId))
            .isInstanceOf(OrchestratorResponseException.class);

        assertThat(this.workflowRepository.count()).isZero();
        assertThat(this.taskPlanRepository.count()).isZero();
    }
}
