package org.vader.core.server.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.instancio.Instancio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.common.model.vader.entity.TaskGraphEntity;
import org.vader.common.model.vader.entity.TaskPlanEntity;
import org.vader.common.model.vader.entity.TaskUpdateAuthor;
import org.vader.common.model.vader.entity.TaskUpdateType;
import org.vader.common.model.vader.entity.WorkflowEntity;
import org.vader.common.model.vader.entity.WorkflowStatus;
import org.vader.core.server.events.EventPublishingFacade;
import org.vader.core.server.workflow.TaskAttemptRepository;
import org.vader.core.server.workflow.TaskAttemptSettledEvent;
import org.vader.core.server.workflow.TaskUpdateService;

class TaskAttemptReviewFallbackServiceTest {

    private TaskAttemptRepository taskAttemptRepository;
    private TaskUpdateService taskUpdateService;
    private EventPublishingFacade eventPublishingFacade;
    private TaskAttemptReviewRetryService retryService;
    private ReattemptDecisionService reattemptDecisionService;
    private TaskAttemptReviewFallbackService service;

    @BeforeEach
    void setUp() {
        this.taskAttemptRepository = mock(TaskAttemptRepository.class);
        this.taskUpdateService = mock(TaskUpdateService.class);
        this.eventPublishingFacade = mock(EventPublishingFacade.class);
        this.retryService = mock(TaskAttemptReviewRetryService.class);
        this.reattemptDecisionService = mock(ReattemptDecisionService.class);

        this.service = new TaskAttemptReviewFallbackService();
        ReflectionTestUtils.setField(
            this.service, "taskAttemptRepository", this.taskAttemptRepository);
        ReflectionTestUtils.setField(this.service, "taskUpdateService", this.taskUpdateService);
        ReflectionTestUtils.setField(
            this.service, "eventPublishingFacade", this.eventPublishingFacade);
        ReflectionTestUtils.setField(this.service, "retryService", this.retryService);
        ReflectionTestUtils.setField(
            this.service, "reattemptDecisionService", this.reattemptDecisionService);
    }

    private TaskAttemptEntity storedAttemptInWorkflow(final WorkflowStatus status) {
        var workflow = new WorkflowEntity();
        workflow.setId(Instancio.create(String.class));
        workflow.setStatus(status);
        var taskPlan = new TaskPlanEntity();
        taskPlan.setWorkflow(workflow);
        var taskGraph = new TaskGraphEntity();
        taskGraph.setTaskPlan(taskPlan);
        var task = new TaskEntity();
        task.setTaskGraph(taskGraph);
        var attempt = new TaskAttemptEntity();
        attempt.setId(Instancio.create(String.class));
        attempt.setTask(task);
        when(this.taskAttemptRepository.findById(attempt.getId()))
            .thenReturn(Optional.of(attempt));
        return attempt;
    }

    @Test
    void settleUnreviewed_recordsAFailedSystemVerdictThatJudgesNothing() {
        var attempt = this.storedAttemptInWorkflow(WorkflowStatus.RUNNING);

        this.service.settleUnreviewed(attempt.getId());

        verify(this.taskUpdateService).record(
            attempt.getTask(), attempt, TaskUpdateType.FAILED,
            TaskAttemptReviewFallbackService.UNREVIEWED_VERDICT, TaskUpdateAuthor.SYSTEM);
        assertThat(TaskAttemptReviewFallbackService.UNREVIEWED_VERDICT)
            .contains("could not be reviewed")
            .contains("without any judgment of its work");
    }

    @Test
    void settleUnreviewed_reattemptsWithoutAnLlmDecisionAndLetsTheWorkflowMoveOn() {
        var attempt = this.storedAttemptInWorkflow(WorkflowStatus.AWAITING_LLM);

        this.service.settleUnreviewed(attempt.getId());

        verify(this.reattemptDecisionService).reattemptIfAttemptsRemain(attempt.getId());
        verify(this.reattemptDecisionService, never()).decideReattempt(anyString());
        verify(this.retryService).resumeIfNoLongerWaiting(attempt);
        var eventCaptor = ArgumentCaptor.forClass(TaskAttemptSettledEvent.class);
        verify(this.eventPublishingFacade).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isEqualTo(new TaskAttemptSettledEvent(
            attempt.getTask().owningTaskGraph().getTaskPlan().getWorkflow().getId(),
            attempt.getId()));
    }

    @Test
    void settleUnreviewed_forACancelledWorkflow_doesNothing() {
        var attempt = this.storedAttemptInWorkflow(WorkflowStatus.CANCELLED);

        this.service.settleUnreviewed(attempt.getId());

        verify(this.taskUpdateService, never()).record(any(), any(), any(), any(), any());
        verify(this.reattemptDecisionService, never()).reattemptIfAttemptsRemain(anyString());
        verify(this.eventPublishingFacade, never()).publish(any());
    }
}
