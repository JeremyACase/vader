package org.vader.core.server.service.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
import org.vader.common.model.vader.entity.WorkflowEntity;
import org.vader.core.server.models.ResultRequest;
import org.vader.core.server.models.TaskAttemptSettledEvent;
import org.vader.core.server.repository.TaskAttemptRepository;

class TaskAttemptServiceTest {

    private static final String ATTEMPT_ID = "aaaaaaaa-1111-2222-3333-444444444444";
    private static final String WORKFLOW_ID = "wwwwwwww-1111-2222-3333-444444444444";

    private TaskAttemptRepository taskAttemptRepository;
    private ApplicationEventPublisher eventPublisher;
    private TaskAttemptService service;

    @BeforeEach
    void setUp() {
        this.taskAttemptRepository = mock(TaskAttemptRepository.class);
        this.eventPublisher = mock(ApplicationEventPublisher.class);

        this.service = new TaskAttemptService();
        ReflectionTestUtils.setField(
            this.service, "taskAttemptRepository", this.taskAttemptRepository);
        ReflectionTestUtils.setField(this.service, "eventPublisher", this.eventPublisher);
    }

    private static TaskAttemptEntity attemptInWorkflow(final String workflowId) {
        var workflow = new WorkflowEntity();
        workflow.setId(workflowId);
        var taskPlan = new TaskPlanEntity();
        taskPlan.setWorkflow(workflow);
        var taskGraph = new TaskGraphEntity();
        taskGraph.setTaskPlan(taskPlan);
        var task = new TaskEntity();
        task.setTaskGraph(taskGraph);

        var attempt = new TaskAttemptEntity();
        attempt.setId(ATTEMPT_ID);
        attempt.setTask(task);
        attempt.setStatus(TaskAttemptStatus.RUNNING);
        return attempt;
    }

    @Test
    void submitResult_publishesSettlementWithTheWorkflowAndAssignmentIds() {
        var attempt = attemptInWorkflow(WORKFLOW_ID);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));

        this.service.submitResult(
            ATTEMPT_ID, new ResultRequest(TaskAttemptStatus.SUCCEEDED, "done", null));

        var eventCaptor = ArgumentCaptor.forClass(TaskAttemptSettledEvent.class);
        verify(this.eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().workflowId()).isEqualTo(WORKFLOW_ID);
        assertThat(eventCaptor.getValue().assignmentId()).isEqualTo(ATTEMPT_ID);
        assertThat(attempt.getStatus()).isEqualTo(TaskAttemptStatus.SUCCEEDED);
    }

    @Test
    void markDispatchFailed_alsoPublishesSettlementForTheFailedAssignment() {
        var attempt = attemptInWorkflow(WORKFLOW_ID);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));

        this.service.markDispatchFailed(ATTEMPT_ID, "could not create Job");

        var eventCaptor = ArgumentCaptor.forClass(TaskAttemptSettledEvent.class);
        verify(this.eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().assignmentId()).isEqualTo(ATTEMPT_ID);
        assertThat(attempt.getStatus()).isEqualTo(TaskAttemptStatus.FAILED);
    }
}
