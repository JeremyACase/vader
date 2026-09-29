package org.vader.core.server.service.agent.decomposition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.model.vader.dto.ClientPrompt;
import org.vader.common.model.vader.entity.ClientPromptEntity;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskAttemptStatus;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.common.model.vader.entity.TaskUpdateType;
import org.vader.core.server.models.llm.RemainingSubtask;
import org.vader.core.server.models.llm.TaskPlanRefinementVerdict;
import org.vader.core.server.repository.ClientPromptRepository;
import org.vader.core.server.repository.TaskAttemptRepository;
import org.vader.core.server.repository.TaskRepository;
import org.vader.core.server.repository.TaskUpdateRepository;
import org.vader.core.server.service.agent.evaluator.strategies.interfaces.InterfaceEvaluatorStrategy;
import org.vader.core.server.service.agent.orchestrator.OrchestratorAgentService;
import org.vader.core.server.service.agent.orchestrator.strategies.interfaces.InterfaceLlmOrchestrationStrategy;
import org.vader.core.server.service.agent.orchestrator.strategies.interfaces.InterfaceReattemptDecisionStrategy;
import org.vader.core.server.service.agent.orchestrator.strategies.interfaces.InterfaceTaskPlanRefinementStrategy;
import org.vader.core.server.service.strategies.inference.InterfaceInferenceGatewayStrategy;
import org.vader.core.server.service.strategies.synthesis.interfaces.InterfaceWorkflowSynthesisStrategy;

/**
 * Runs the saga against the real (H2) persistence layer: entity ids are assigned up front, so
 * Spring Data merges rather than persists new subtasks, and only a database round trip shows
 * whether the chain, the parent link, and the rolled-up result actually survive.
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = "vader.scheduling.enabled=false")
class TaskDecompositionSagaIntegrationTest {

    private static final String PLAN = """
        {
          "objective": "Clean a spreadsheet",
          "taskGraph": {
            "tasks": [
              { "title": "Clean and validate data", "description": "Clean the spreadsheet." }
            ]
          }
        }
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

    @MockitoBean
    private InterfaceTaskPlanRefinementStrategy taskPlanRefinementStrategy;

    @Autowired
    private OrchestratorAgentService orchestratorAgentService;

    @Autowired
    private TaskDecompositionSaga saga;

    @Autowired
    private ClientPromptRepository clientPromptRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TaskAttemptRepository taskAttemptRepository;

    @Autowired
    private TaskUpdateRepository taskUpdateRepository;

    @Autowired
    private EntityManager entityManager;

    private TaskAttemptEntity decomposableAttempt() {
        when(this.orchestrator.orchestrate(any(ClientPrompt.class), any())).thenReturn(PLAN);
        when(this.taskPlanRefinementStrategy.critique(any()))
            .thenReturn(new TaskPlanRefinementVerdict(false, "approved"));
        var prompt = new ClientPromptEntity();
        prompt.setText("Analyze this spreadsheet.");
        var workflow = this.orchestratorAgentService.decompose(
            this.clientPromptRepository.save(prompt).getId());
        var task = workflow.getTaskPlan().getTaskGraph().getTasks().iterator().next();

        var attempt = new TaskAttemptEntity();
        attempt.setTask(task);
        attempt.setAttemptNumber(1);
        attempt.setStatus(TaskAttemptStatus.SUCCEEDED);
        attempt.setResult("Here's the corrected approach. Let's proceed.");
        return this.taskAttemptRepository.save(attempt);
    }

    private TaskAttemptEntity reloaded(final TaskAttemptEntity attempt) {
        this.entityManager.flush();
        this.entityManager.clear();
        return this.taskAttemptRepository.findById(attempt.getId()).orElseThrow();
    }

    private void succeed(final TaskEntity subtask, final String result) {
        var attempt = new TaskAttemptEntity();
        attempt.setTask(subtask);
        attempt.setAttemptNumber(1);
        attempt.setStatus(TaskAttemptStatus.SUCCEEDED);
        attempt.setResult(result);
        this.taskAttemptRepository.save(attempt);
    }

    @Test
    void decompose_persistsChainedSubtasksThatResolveTheirWorkflowThroughTheParent() {
        var attempt = this.decomposableAttempt();
        final var graphId = attempt.getTask().owningTaskGraph().getId();

        this.saga.decompose(attempt, List.of(
            new RemainingSubtask("Run the fix", "Run the corrected cleaning code."),
            new RemainingSubtask("Verify", "Confirm no missing values remain.")),
            "code never ran");

        var subtasks = this.saga.subtasksOf(this.reloaded(attempt));
        assertThat(subtasks).extracting(TaskEntity::getTitle)
            .containsExactly("Run the fix", "Verify");
        assertThat(subtasks.get(1).getDependsOn()).containsExactly(subtasks.get(0));
        assertThat(subtasks).allSatisfy(subtask -> {
            assertThat(subtask.getTaskGraph()).isNull();
            assertThat(subtask.depth()).isEqualTo(1);
            assertThat(subtask.owningTaskGraph().getId()).isEqualTo(graphId);
            assertThat(subtask.getSpawnedByAttempt().getId()).isEqualTo(attempt.getId());
        });
        assertThat(this.taskRepository.findById(subtasks.get(0).getId())).isPresent();
        assertThat(this.taskUpdateRepository.findFirstByTaskAttemptIdAndTypeInOrderByCreatedAtDesc(
                attempt.getId(), List.of(TaskUpdateType.DECOMPOSED)))
            .isPresent();
    }

    @Test
    void rollUp_persistsTheConcatenatedSubtaskResultsOnTheDecomposedAttempt() {
        var attempt = this.decomposableAttempt();
        this.saga.decompose(attempt, List.of(
            new RemainingSubtask("Run the fix", "Run the corrected cleaning code."),
            new RemainingSubtask("Verify", "Confirm no missing values remain.")),
            "code never ran");
        var subtasks = this.saga.subtasksOf(this.reloaded(attempt));
        this.succeed(subtasks.get(0), "Filled 12 missing values.");
        this.succeed(subtasks.get(1), "No missing values remain.");

        this.saga.rollUp(this.reloaded(attempt));

        var rolledUp = this.reloaded(attempt);
        assertThat(rolledUp.getResult()).contains("Let's proceed.");
        assertThat(rolledUp.effectiveResult())
            .contains("Filled 12 missing values.", "No missing values remain.")
            .doesNotContain("Let's proceed.");
    }
}
