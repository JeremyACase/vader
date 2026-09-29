package org.vader.core.server.taskagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import org.vader.common.model.vader.entity.TaskAttemptStatus;
import org.vader.common.model.vader.entity.TaskUpdateAuthor;
import org.vader.common.model.vader.entity.TaskUpdateType;
import org.vader.core.server.taskagent.model.ResultRequest;
import org.vader.core.server.workflow.TaskAttemptRepository;
import org.vader.core.server.workflow.TaskAttemptSettledEvent;
import org.vader.core.server.workflow.TaskUpdateService;

class TaskAttemptLifecycleServiceTest {

    private static final String ATTEMPT_ID = TaskAttemptObjectMother.ATTEMPT_ID;
    private static final String WORKFLOW_ID = "wwwwwwww-1111-2222-3333-444444444444";

    private TaskAttemptRepository taskAttemptRepository;
    private ApplicationEventPublisher eventPublisher;
    private TaskUpdateService taskUpdateService;
    private AssignmentContextBuilder assignmentContextBuilder;
    private TaskAttemptLifecycleService service;

    @BeforeEach
    void setUp() {
        this.taskAttemptRepository = mock(TaskAttemptRepository.class);
        this.eventPublisher = mock(ApplicationEventPublisher.class);
        this.taskUpdateService = mock(TaskUpdateService.class);
        this.assignmentContextBuilder = mock(AssignmentContextBuilder.class);

        this.service = new TaskAttemptLifecycleService();
        ReflectionTestUtils.setField(
            this.service, "taskAttemptRepository", this.taskAttemptRepository);
        ReflectionTestUtils.setField(this.service, "eventPublisher", this.eventPublisher);
        ReflectionTestUtils.setField(this.service, "taskUpdateService", this.taskUpdateService);
        ReflectionTestUtils.setField(
            this.service, "assignmentContextBuilder", this.assignmentContextBuilder);
    }

    @Test
    void fetchAssignment_onFirstContact_recordsRunningUpdateAuthoredByTheTaskAgent() {
        var attempt = TaskAttemptObjectMother.attemptInWorkflow(WORKFLOW_ID);
        attempt.setStatus(TaskAttemptStatus.DISPATCHED);
        attempt.setAttemptNumber(1);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));

        this.service.fetchAssignment(ATTEMPT_ID);

        verify(this.taskUpdateService).record(
            attempt.getTask(), attempt, TaskUpdateType.RUNNING,
            "Attempt 1 started running.", TaskUpdateAuthor.TASK_AGENT);
    }

    @Test
    void fetchAssignment_whenAlreadyRunning_doesNotRecordAnotherRunningUpdate() {
        var attempt = TaskAttemptObjectMother.attemptInWorkflow(WORKFLOW_ID);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));

        this.service.fetchAssignment(ATTEMPT_ID);

        verify(this.taskUpdateService, never()).record(
            any(), any(), any(), anyString(), any());
    }

    @Test
    void submitResult_publishesSettlementWithTheWorkflowAndAssignmentIds() {
        var attempt = TaskAttemptObjectMother.attemptInWorkflow(WORKFLOW_ID);
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
        var attempt = TaskAttemptObjectMother.attemptInWorkflow(WORKFLOW_ID);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));

        this.service.markDispatchFailed(ATTEMPT_ID, "could not create Job");

        var eventCaptor = ArgumentCaptor.forClass(TaskAttemptSettledEvent.class);
        verify(this.eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().assignmentId()).isEqualTo(ATTEMPT_ID);
        assertThat(attempt.getStatus()).isEqualTo(TaskAttemptStatus.FAILED);
    }

    @Test
    void fetchAssignment_returnsTheTaskDescriptionAsObjectiveAlongsideTheBuiltContext() {
        var attempt = TaskAttemptObjectMother.attemptInWorkflow(WORKFLOW_ID);
        attempt.getTask().setDescription("Run the corrected cleaning code.");
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        when(this.assignmentContextBuilder.build(attempt.getTask())).thenReturn("the context");

        var response = this.service.fetchAssignment(ATTEMPT_ID);

        assertThat(response.objective()).isEqualTo("Run the corrected cleaning code.");
        assertThat(response.context()).isEqualTo("the context");
    }
}
