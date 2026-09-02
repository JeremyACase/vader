package org.vader.core.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.model.vader.dto.ClientPrompt;
import org.vader.common.model.vader.entity.ClientPromptEntity;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.core.server.orchestrator.OrchestratorResponseException;
import org.vader.core.server.orchestrator.interfaces.InterfaceLlmOrchestrationStrategy;
import org.vader.core.server.repository.ClientPromptRepository;
import org.vader.core.server.repository.TaskPlanRepository;
import org.vader.core.server.repository.WorkflowRepository;

@SpringBootTest
@Transactional
class WorkflowServiceIntegrationTest {

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
    private InterfaceLlmOrchestrationStrategy orchestrator;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private TaskPlanRepository taskPlanRepository;

    @Autowired
    private ClientPromptRepository clientPromptRepository;

    private static ClientPromptEntity prompt(final String text) {
        var prompt = new ClientPromptEntity();
        prompt.setText(text);
        return prompt;
    }

    @Test
    void decompose_persistsTheDecompositionUnderWorkflowAndLinksThePlanBackToIt() {
        when(this.orchestrator.orchestrate(any(ClientPrompt.class))).thenReturn(VALID_PLAN);

        var saved = this.workflowService.decompose(prompt("Help me ship onboarding"));

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
    void decompose_whenResponseFailsSchema_throwsAndPersistsNothing() {
        when(this.orchestrator.orchestrate(any(ClientPrompt.class)))
            .thenReturn("{\"objective\":\"no task graph here\"}");

        assertThatThrownBy(() -> this.workflowService.decompose(prompt("whatever")))
            .isInstanceOf(OrchestratorResponseException.class);

        assertThat(this.workflowRepository.count()).isZero();
        assertThat(this.taskPlanRepository.count()).isZero();
        assertThat(this.clientPromptRepository.count()).isZero();
    }
}
