package org.vader.core.server.service.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.model.vader.entity.OutboxMessageStatus;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskAttemptReviewOutboxMessageEntity;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.common.model.vader.entity.TaskGraphEntity;
import org.vader.common.model.vader.entity.TaskPlanEntity;
import org.vader.common.model.vader.entity.TaskUpdateAuthor;
import org.vader.common.model.vader.entity.TaskUpdateType;
import org.vader.common.model.vader.entity.WorkflowEntity;
import org.vader.common.model.vader.entity.WorkflowStatus;
import org.vader.core.server.repository.TaskAttemptReviewOutboxMessageRepository;
import org.vader.core.server.repository.WorkflowRepository;

class TaskAttemptReviewRetryServiceTest {

    private static final long RETRY_INTERVAL_MS = 30_000L;

    private TaskAttemptReviewOutboxMessageRepository messageRepository;
    private WorkflowRepository workflowRepository;
    private TaskUpdateService taskUpdateService;
    private TaskAttemptReviewRetryService service;

    @BeforeEach
    void setUp() {
        this.messageRepository = mock(TaskAttemptReviewOutboxMessageRepository.class);
        this.workflowRepository = mock(WorkflowRepository.class);
        this.taskUpdateService = mock(TaskUpdateService.class);
        this.service = new TaskAttemptReviewRetryService();
        ReflectionTestUtils.setField(this.service, "messageRepository", this.messageRepository);
        ReflectionTestUtils.setField(this.service, "workflowRepository", this.workflowRepository);
        ReflectionTestUtils.setField(this.service, "taskUpdateService", this.taskUpdateService);
        ReflectionTestUtils.setField(this.service, "llmRetryIntervalMs", RETRY_INTERVAL_MS);
    }

    private static TaskAttemptEntity attemptInWorkflowWithStatus(final WorkflowStatus status) {
        var workflow = new WorkflowEntity();
        workflow.setId(UUID.randomUUID().toString());
        workflow.setStatus(status);
        var taskPlan = new TaskPlanEntity();
        taskPlan.setWorkflow(workflow);
        var taskGraph = new TaskGraphEntity();
        taskGraph.setTaskPlan(taskPlan);
        var task = new TaskEntity();
        task.setTaskGraph(taskGraph);
        var attempt = new TaskAttemptEntity();
        attempt.setId(UUID.randomUUID().toString());
        attempt.setTask(task);
        return attempt;
    }

    private static WorkflowEntity workflowOf(final TaskAttemptEntity attempt) {
        return attempt.getTask().getTaskGraph().getTaskPlan().getWorkflow();
    }

    private TaskAttemptReviewOutboxMessageEntity claimedReviewOf(final TaskAttemptEntity attempt) {
        var message = new TaskAttemptReviewOutboxMessageEntity();
        message.setId(UUID.randomUUID().toString());
        message.setStatus(OutboxMessageStatus.CLAIMED);
        message.setTaskAttempt(attempt);
        when(this.messageRepository.findById(message.getId())).thenReturn(Optional.of(message));
        return message;
    }

    @Test
    void deferForLlmOutage_putsTheReviewBackOnTheQueueUntilTheRetryInterval() {
        final var before = OffsetDateTime.now();
        var message = claimedReviewOf(attemptInWorkflowWithStatus(WorkflowStatus.RUNNING));

        this.service.deferForLlmOutage(message.getId(), "connection refused");

        assertThat(message.getStatus()).isEqualTo(OutboxMessageStatus.PENDING);
        assertThat(message.getFailureReason()).isEqualTo("connection refused");
        assertThat(message.getNextAttemptAt())
            .isAfterOrEqualTo(before.plusNanos(RETRY_INTERVAL_MS * 1_000_000));
        verify(this.messageRepository).save(message);
    }

    @Test
    void deferForLlmOutage_marksRunningWorkflowAwaitingTheLlmAndRecordsWhy() {
        var attempt = attemptInWorkflowWithStatus(WorkflowStatus.RUNNING);
        var message = claimedReviewOf(attempt);

        this.service.deferForLlmOutage(message.getId(), "connection refused");

        assertThat(workflowOf(attempt).getStatus()).isEqualTo(WorkflowStatus.AWAITING_LLM);
        verify(this.workflowRepository).save(workflowOf(attempt));
        verify(this.taskUpdateService).record(
            eq(attempt.getTask()), eq(attempt), eq(TaskUpdateType.UPDATE),
            contains("connection refused"), eq(TaskUpdateAuthor.SYSTEM));
    }

    @Test
    void deferForLlmOutage_whenAlreadyAwaiting_doesNotRecordAnotherUpdate() {
        var attempt = attemptInWorkflowWithStatus(WorkflowStatus.AWAITING_LLM);
        var message = claimedReviewOf(attempt);

        this.service.deferForLlmOutage(message.getId(), "connection refused");

        assertThat(workflowOf(attempt).getStatus()).isEqualTo(WorkflowStatus.AWAITING_LLM);
        verify(this.taskUpdateService, never()).record(any(), any(), any(), anyString(), any());
    }

    @Test
    void resumeIfNoLongerWaiting_whenNothingElseIsDeferred_marksTheWorkflowRunning() {
        var attempt = attemptInWorkflowWithStatus(WorkflowStatus.AWAITING_LLM);
        when(this.messageRepository.existsDeferredInWorkflow(
            workflowOf(attempt).getId(), OutboxMessageStatus.PENDING)).thenReturn(false);

        this.service.resumeIfNoLongerWaiting(attempt);

        assertThat(workflowOf(attempt).getStatus()).isEqualTo(WorkflowStatus.RUNNING);
        verify(this.workflowRepository).save(workflowOf(attempt));
    }

    @Test
    void resumeIfNoLongerWaiting_whenAnotherReviewIsStillDeferred_keepsAwaiting() {
        var attempt = attemptInWorkflowWithStatus(WorkflowStatus.AWAITING_LLM);
        when(this.messageRepository.existsDeferredInWorkflow(
            workflowOf(attempt).getId(), OutboxMessageStatus.PENDING)).thenReturn(true);

        this.service.resumeIfNoLongerWaiting(attempt);

        assertThat(workflowOf(attempt).getStatus()).isEqualTo(WorkflowStatus.AWAITING_LLM);
        verify(this.workflowRepository, never()).save(any());
    }

    @Test
    void resumeIfNoLongerWaiting_leavesRunningWorkflowAlone() {
        var attempt = attemptInWorkflowWithStatus(WorkflowStatus.RUNNING);

        this.service.resumeIfNoLongerWaiting(attempt);

        assertThat(workflowOf(attempt).getStatus()).isEqualTo(WorkflowStatus.RUNNING);
        verify(this.workflowRepository, never()).save(any());
    }
}
