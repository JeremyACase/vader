package org.vader.core.server.service.agent.evaluator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
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
import org.vader.core.server.models.EvaluationRequest;
import org.vader.core.server.models.EvaluationVerdict;
import org.vader.core.server.repository.TaskAttemptRepository;
import org.vader.core.server.repository.TaskUpdateRepository;
import org.vader.core.server.service.agent.TaskUpdateService;
import org.vader.core.server.service.agent.evaluator.strategies.interfaces.InterfaceEvaluatorStrategy;

class EvaluatorAgentServiceTest {

    private static final String ATTEMPT_ID = "aaaaaaaa-1111-2222-3333-444444444444";

    private TaskAttemptRepository taskAttemptRepository;
    private TaskUpdateRepository taskUpdateRepository;
    private TaskUpdateService taskUpdateService;
    private InterfaceEvaluatorStrategy evaluatorStrategy;
    private EvaluatorAgentService service;

    @BeforeEach
    void setUp() {
        this.taskAttemptRepository = mock(TaskAttemptRepository.class);
        this.taskUpdateRepository = mock(TaskUpdateRepository.class);
        this.taskUpdateService = mock(TaskUpdateService.class);
        this.evaluatorStrategy = mock(InterfaceEvaluatorStrategy.class);

        this.service = new EvaluatorAgentService();
        ReflectionTestUtils.setField(
            this.service, "taskAttemptRepository", this.taskAttemptRepository);
        ReflectionTestUtils.setField(
            this.service, "taskUpdateRepository", this.taskUpdateRepository);
        ReflectionTestUtils.setField(this.service, "taskUpdateService", this.taskUpdateService);
        ReflectionTestUtils.setField(this.service, "evaluatorStrategy", this.evaluatorStrategy);
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
}
