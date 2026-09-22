package org.vader.core.server.service.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskAttemptStatus;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.common.model.vader.entity.TaskGraphEntity;
import org.vader.common.model.vader.entity.TaskPlanEntity;
import org.vader.common.model.vader.entity.TaskUpdateAuthor;
import org.vader.common.model.vader.entity.TaskUpdateType;
import org.vader.common.model.vader.entity.WorkflowEntity;
import org.vader.core.server.models.TaskAttemptSettledEvent;
import org.vader.core.server.repository.TaskAttemptRepository;
import org.vader.core.server.service.agent.evaluator.EvaluatorAgentService;
import org.vader.core.server.service.agent.orchestrator.OrchestratorAgentService;

class TaskAttemptReviewServiceTest {

    private static final String ATTEMPT_ID = "aaaaaaaa-1111-2222-3333-444444444444";
    private static final String WORKFLOW_ID = "wwwwwwww-1111-2222-3333-444444444444";

    private TaskAttemptRepository taskAttemptRepository;
    private TaskUpdateService taskUpdateService;
    private ApplicationEventPublisher eventPublisher;
    private EvaluatorAgentService evaluatorAgentService;
    private OrchestratorAgentService orchestratorAgentService;
    private TaskAttemptReviewService service;

    @BeforeEach
    void setUp() {
        this.taskAttemptRepository = mock(TaskAttemptRepository.class);
        this.taskUpdateService = mock(TaskUpdateService.class);
        this.eventPublisher = mock(ApplicationEventPublisher.class);
        this.evaluatorAgentService = mock(EvaluatorAgentService.class);
        this.orchestratorAgentService = mock(OrchestratorAgentService.class);

        this.service = new TaskAttemptReviewService();
        ReflectionTestUtils.setField(
            this.service, "taskAttemptRepository", this.taskAttemptRepository);
        ReflectionTestUtils.setField(this.service, "taskUpdateService", this.taskUpdateService);
        ReflectionTestUtils.setField(this.service, "eventPublisher", this.eventPublisher);
        ReflectionTestUtils.setField(
            this.service, "evaluatorAgentService", this.evaluatorAgentService);
        ReflectionTestUtils.setField(
            this.service, "orchestratorAgentService", this.orchestratorAgentService);
    }

    private static TaskAttemptEntity attemptInWorkflow(final TaskAttemptStatus status) {
        var workflow = new WorkflowEntity();
        workflow.setId(WORKFLOW_ID);
        var taskPlan = new TaskPlanEntity();
        taskPlan.setWorkflow(workflow);
        var taskGraph = new TaskGraphEntity();
        taskGraph.setTaskPlan(taskPlan);
        var task = new TaskEntity();
        task.setId("t1");
        task.setTaskGraph(taskGraph);

        var attempt = new TaskAttemptEntity();
        attempt.setId(ATTEMPT_ID);
        attempt.setTask(task);
        attempt.setStatus(status);
        return attempt;
    }

    @Test
    void review_forSucceededAttempt_delegatesToTheEvaluator() {
        var attempt = attemptInWorkflow(TaskAttemptStatus.SUCCEEDED);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        when(this.evaluatorAgentService.evaluate(ATTEMPT_ID))
            .thenReturn(TaskUpdateType.COMPLETED);

        this.service.review(ATTEMPT_ID);

        verify(this.evaluatorAgentService).evaluate(ATTEMPT_ID);
        verify(this.orchestratorAgentService, never()).decideReattempt(any());
    }

    @Test
    void review_whenEvaluatorFails_alsoAsksTheOrchestratorToDecide() {
        var attempt = attemptInWorkflow(TaskAttemptStatus.FAILED);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        when(this.evaluatorAgentService.evaluate(ATTEMPT_ID)).thenReturn(TaskUpdateType.FAILED);

        this.service.review(ATTEMPT_ID);

        verify(this.orchestratorAgentService).decideReattempt(ATTEMPT_ID);
    }

    @Test
    void review_forTimedOutAttempt_skipsEvaluatorAndRecordsTimedOutVerdict() {
        var attempt = attemptInWorkflow(TaskAttemptStatus.TIMED_OUT);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));

        this.service.review(ATTEMPT_ID);

        verify(this.evaluatorAgentService, never()).evaluate(any());
        verify(this.taskUpdateService).record(
            attempt.getTask(), attempt, TaskUpdateType.TIMED_OUT, "The attempt's deadline elapsed "
                + "before it reported a result.", TaskUpdateAuthor.SYSTEM);
        verify(this.orchestratorAgentService).decideReattempt(ATTEMPT_ID);
    }

    @Test
    void review_forStalledAttempt_skipsEvaluatorAndRecordsFailedVerdict() {
        var attempt = attemptInWorkflow(TaskAttemptStatus.STALLED);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));

        this.service.review(ATTEMPT_ID);

        verify(this.evaluatorAgentService, never()).evaluate(any());
        verify(this.taskUpdateService).record(
            attempt.getTask(), attempt, TaskUpdateType.FAILED,
            "The attempt was reaped for repeating the same action with no progress.",
            TaskUpdateAuthor.SYSTEM);
        verify(this.orchestratorAgentService).decideReattempt(ATTEMPT_ID);
    }

    @Test
    void review_alwaysPublishesSettledEventForTheAttemptsWorkflow() {
        var attempt = attemptInWorkflow(TaskAttemptStatus.SUCCEEDED);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        when(this.evaluatorAgentService.evaluate(ATTEMPT_ID))
            .thenReturn(TaskUpdateType.COMPLETED);

        this.service.review(ATTEMPT_ID);

        var eventCaptor = ArgumentCaptor.forClass(TaskAttemptSettledEvent.class);
        verify(this.eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().workflowId()).isEqualTo(WORKFLOW_ID);
        assertThat(eventCaptor.getValue().assignmentId()).isEqualTo(ATTEMPT_ID);
    }
}
