package org.vader.core.server.service.agent.evaluator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskAttemptStatus;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.common.model.vader.entity.TaskUpdateAuthor;
import org.vader.common.model.vader.entity.TaskUpdateEntity;
import org.vader.common.model.vader.entity.TaskUpdateType;
import org.vader.core.server.models.llm.EvaluationRequest;
import org.vader.core.server.models.llm.EvaluationVerdict;
import org.vader.core.server.models.llm.RemainingSubtask;
import org.vader.core.server.repository.TaskAttemptRepository;
import org.vader.core.server.repository.TaskUpdateRepository;
import org.vader.core.server.service.agent.TaskUpdateService;
import org.vader.core.server.service.agent.decomposition.TaskDecompositionSaga;
import org.vader.core.server.service.agent.evaluator.strategies.interfaces.InterfaceEvaluatorStrategy;

class EvaluatorAgentServiceTest {

    private static final String ATTEMPT_ID = "aaaaaaaa-1111-2222-3333-444444444444";

    private TaskAttemptRepository taskAttemptRepository;
    private TaskUpdateRepository taskUpdateRepository;
    private TaskUpdateService taskUpdateService;
    private InterfaceEvaluatorStrategy evaluatorStrategy;
    private ToolCallEvidenceAdapter toolCallEvidenceAdapter;
    private TaskDecompositionSaga taskDecompositionSaga;
    private EvaluatorAgentService service;

    @BeforeEach
    void setUp() {
        this.taskAttemptRepository = mock(TaskAttemptRepository.class);
        this.taskUpdateRepository = mock(TaskUpdateRepository.class);
        this.taskUpdateService = mock(TaskUpdateService.class);
        this.evaluatorStrategy = mock(InterfaceEvaluatorStrategy.class);
        this.toolCallEvidenceAdapter = mock(ToolCallEvidenceAdapter.class);
        this.taskDecompositionSaga = mock(TaskDecompositionSaga.class);

        this.service = new EvaluatorAgentService();
        ReflectionTestUtils.setField(
            this.service, "taskAttemptRepository", this.taskAttemptRepository);
        ReflectionTestUtils.setField(
            this.service, "taskUpdateRepository", this.taskUpdateRepository);
        ReflectionTestUtils.setField(this.service, "taskUpdateService", this.taskUpdateService);
        ReflectionTestUtils.setField(this.service, "evaluatorStrategy", this.evaluatorStrategy);
        ReflectionTestUtils.setField(
            this.service, "toolCallEvidenceAdapter", this.toolCallEvidenceAdapter);
        ReflectionTestUtils.setField(
            this.service, "taskDecompositionSaga", this.taskDecompositionSaga);
    }

    private TaskEntity task() {
        var task = new TaskEntity();
        task.setId("t1");
        task.setTitle("title");
        task.setDescription("description");
        when(this.taskUpdateRepository.findByTaskIdOrderByCreatedAtAsc("t1"))
            .thenReturn(List.of());
        return task;
    }

    private static List<RemainingSubtask> remainingSteps() {
        return List.of(
            new RemainingSubtask("Run the fix", "Run the corrected cleaning code."),
            new RemainingSubtask("Verify", "Confirm no missing values remain."));
    }

    private static TaskAttemptEntity attempt(
            final TaskEntity task, final TaskAttemptStatus status) {
        var attempt = new TaskAttemptEntity();
        attempt.setId(ATTEMPT_ID);
        attempt.setTask(task);
        attempt.setStatus(status);
        attempt.setResult("the result");
        attempt.setFailureReason("the failure reason");
        return attempt;
    }

    @Test
    void evaluate_whenStrategyPasses_recordsCompletedVerdict() {
        var task = new TaskEntity();
        task.setId("t1");
        task.setTitle("title");
        task.setDescription("description");
        var attempt = attempt(task, TaskAttemptStatus.SUCCEEDED);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        when(this.taskUpdateRepository.findByTaskIdOrderByCreatedAtAsc("t1"))
            .thenReturn(List.of());
        when(this.evaluatorStrategy.evaluate(any()))
            .thenReturn(new EvaluationVerdict(true, "genuinely done"));

        var result = this.service.evaluate(ATTEMPT_ID);

        assertThat(result).isEqualTo(TaskUpdateType.COMPLETED);
        verify(this.taskUpdateService).record(
            same(task), same(attempt), eq(TaskUpdateType.COMPLETED),
            eq("genuinely done"), eq(TaskUpdateAuthor.EVALUATOR));
    }

    @Test
    void evaluate_whenStrategyFails_recordsFailedVerdict() {
        var task = new TaskEntity();
        task.setId("t1");
        task.setTitle("title");
        task.setDescription("description");
        var attempt = attempt(task, TaskAttemptStatus.SUCCEEDED);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        when(this.taskUpdateRepository.findByTaskIdOrderByCreatedAtAsc("t1"))
            .thenReturn(List.of());
        when(this.evaluatorStrategy.evaluate(any()))
            .thenReturn(new EvaluationVerdict(false, "actually incomplete"));

        var result = this.service.evaluate(ATTEMPT_ID);

        assertThat(result).isEqualTo(TaskUpdateType.FAILED);
        verify(this.taskUpdateService).record(
            same(task), same(attempt), eq(TaskUpdateType.FAILED),
            eq("actually incomplete"), eq(TaskUpdateAuthor.EVALUATOR));
    }

    @Test
    void evaluate_includesPriorUpdatesAndAttemptOutcomeInTheRequest() {
        var task = new TaskEntity();
        task.setId("t1");
        task.setTitle("title");
        task.setDescription("description");
        var attempt = attempt(task, TaskAttemptStatus.FAILED);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        var priorUpdate = new TaskUpdateEntity();
        priorUpdate.setType(TaskUpdateType.UPDATE);
        priorUpdate.setDescription("tried approach A");
        when(this.taskUpdateRepository.findByTaskIdOrderByCreatedAtAsc("t1"))
            .thenReturn(List.of(priorUpdate));
        when(this.evaluatorStrategy.evaluate(any()))
            .thenReturn(new EvaluationVerdict(false, "still broken"));

        this.service.evaluate(ATTEMPT_ID);

        var requestCaptor = ArgumentCaptor.forClass(EvaluationRequest.class);
        verify(this.evaluatorStrategy).evaluate(requestCaptor.capture());
        var request = requestCaptor.getValue();
        assertThat(request.taskTitle()).isEqualTo("title");
        assertThat(request.attemptStatus()).isEqualTo(TaskAttemptStatus.FAILED);
        assertThat(request.attemptFailureReason()).isEqualTo("the failure reason");
        assertThat(request.priorUpdateDescriptions()).hasSize(1)
            .first().asString().contains("tried approach A");
    }

    @Test
    void evaluate_whenUnfinishedWithRemainingSteps_decomposesInsteadOfFailing() {
        var task = this.task();
        var attempt = attempt(task, TaskAttemptStatus.SUCCEEDED);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        when(this.taskDecompositionSaga.canDecompose(task)).thenReturn(true);
        when(this.evaluatorStrategy.evaluate(any()))
            .thenReturn(new EvaluationVerdict(false, "code never ran", remainingSteps()));

        var result = this.service.evaluate(ATTEMPT_ID);

        assertThat(result).isEqualTo(TaskUpdateType.DECOMPOSED);
        verify(this.taskDecompositionSaga)
            .decompose(same(attempt), eq(remainingSteps()), eq("code never ran"));
        verify(this.taskUpdateService, never())
            .record(any(), any(), eq(TaskUpdateType.FAILED), any(), any());
    }

    @Test
    void evaluate_whenTaskCannotDecompose_failsDespiteRemainingSteps() {
        var task = this.task();
        var attempt = attempt(task, TaskAttemptStatus.SUCCEEDED);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        when(this.taskDecompositionSaga.canDecompose(task)).thenReturn(false);
        when(this.evaluatorStrategy.evaluate(any()))
            .thenReturn(new EvaluationVerdict(false, "code never ran", remainingSteps()));

        var result = this.service.evaluate(ATTEMPT_ID);

        assertThat(result).isEqualTo(TaskUpdateType.FAILED);
        verify(this.taskDecompositionSaga, never()).decompose(any(), any(), any());
        verify(this.taskUpdateService).record(
            same(task), same(attempt), eq(TaskUpdateType.FAILED),
            eq("code never ran"), eq(TaskUpdateAuthor.EVALUATOR));
    }

    @Test
    void evaluate_whenPassed_completesEvenIfRemainingStepsWereListed() {
        var task = this.task();
        var attempt = attempt(task, TaskAttemptStatus.SUCCEEDED);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        when(this.taskDecompositionSaga.canDecompose(task)).thenReturn(true);
        when(this.evaluatorStrategy.evaluate(any()))
            .thenReturn(new EvaluationVerdict(true, "done", remainingSteps()));

        var result = this.service.evaluate(ATTEMPT_ID);

        assertThat(result).isEqualTo(TaskUpdateType.COMPLETED);
        verify(this.taskDecompositionSaga, never()).decompose(any(), any(), any());
    }

    @Test
    void evaluate_includesTheLastToolCallEvidenceInTheRequest() {
        var task = this.task();
        var attempt = attempt(task, TaskAttemptStatus.SUCCEEDED);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        when(this.toolCallEvidenceAdapter.lastToolCallEvidence(ATTEMPT_ID))
            .thenReturn("The agent's last tool call was run_python_code: it FAILED.");
        when(this.evaluatorStrategy.evaluate(any()))
            .thenReturn(new EvaluationVerdict(false, "failed"));

        this.service.evaluate(ATTEMPT_ID);

        var requestCaptor = ArgumentCaptor.forClass(EvaluationRequest.class);
        verify(this.evaluatorStrategy).evaluate(requestCaptor.capture());
        assertThat(requestCaptor.getValue().lastToolCallEvidence()).contains("FAILED");
    }

    @Test
    void verdict_normalizesOmittedRemainingSubtasksToEmpty() {
        assertThat(new EvaluationVerdict(false, "reason", null).remainingSubtasks()).isEmpty();
    }
}
