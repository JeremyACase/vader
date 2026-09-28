package org.vader.core.server.service.agent.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskAttemptStatus;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.common.model.vader.entity.TaskGraphEntity;
import org.vader.common.model.vader.entity.TaskPlanEntity;
import org.vader.common.model.vader.entity.TaskUpdateEntity;
import org.vader.common.model.vader.entity.TaskUpdateType;
import org.vader.common.model.vader.entity.WorkflowEntity;
import org.vader.common.model.vader.entity.WorkflowStatus;
import org.vader.core.server.repository.TaskAttemptRepository;
import org.vader.core.server.repository.TaskAttemptReviewOutboxMessageRepository;
import org.vader.core.server.repository.TaskUpdateRepository;
import org.vader.core.server.repository.WorkflowRepository;
import org.vader.core.server.service.agent.WorkflowSynthesisService;
import org.vader.core.server.service.io.TaskAssignmentOutbox;
import org.vader.core.server.service.io.TaskAttemptReviewOutbox;

class TaskGraphSchedulerTest {

    private static final String WORKFLOW_ID = "wwwwwwww-1111-2222-3333-444444444444";

    private WorkflowRepository workflowRepository;
    private TaskAttemptRepository taskAttemptRepository;
    private TaskUpdateRepository taskUpdateRepository;
    private TaskAssignmentOutbox taskAssignmentOutbox;
    private TaskAttemptReviewOutbox taskAttemptReviewOutbox;
    private TaskAttemptReviewOutboxMessageRepository taskAttemptReviewOutboxMessageRepository;
    private WorkflowSynthesisService workflowSynthesisService;
    private TaskGraphScheduler scheduler;

    @BeforeEach
    void setUp() {
        this.workflowRepository = mock(WorkflowRepository.class);
        this.taskAttemptRepository = mock(TaskAttemptRepository.class);
        this.taskUpdateRepository = mock(TaskUpdateRepository.class);
        this.taskAssignmentOutbox = mock(TaskAssignmentOutbox.class);
        this.taskAttemptReviewOutbox = mock(TaskAttemptReviewOutbox.class);
        this.taskAttemptReviewOutboxMessageRepository =
            mock(TaskAttemptReviewOutboxMessageRepository.class);
        this.workflowSynthesisService = mock(WorkflowSynthesisService.class);

        this.scheduler = new TaskGraphScheduler();
        ReflectionTestUtils.setField(
            this.scheduler, "workflowRepository", this.workflowRepository);
        ReflectionTestUtils.setField(
            this.scheduler, "taskAttemptRepository", this.taskAttemptRepository);
        ReflectionTestUtils.setField(
            this.scheduler, "taskUpdateRepository", this.taskUpdateRepository);
        ReflectionTestUtils.setField(
            this.scheduler, "taskAssignmentOutbox", this.taskAssignmentOutbox);
        ReflectionTestUtils.setField(
            this.scheduler, "taskAttemptReviewOutbox", this.taskAttemptReviewOutbox);
        ReflectionTestUtils.setField(this.scheduler,
            "taskAttemptReviewOutboxMessageRepository",
            this.taskAttemptReviewOutboxMessageRepository);
        ReflectionTestUtils.setField(
            this.scheduler, "workflowSynthesisService", this.workflowSynthesisService);
    }

    private static WorkflowEntity runningWorkflowWithOneTask(final TaskEntity task) {
        var taskGraph = new TaskGraphEntity();
        taskGraph.setTasks(Set.of(task));
        var taskPlan = new TaskPlanEntity();
        taskPlan.setTaskGraph(taskGraph);
        var workflow = new WorkflowEntity();
        workflow.setId(WORKFLOW_ID);
        workflow.setTaskPlan(taskPlan);
        workflow.setStatus(WorkflowStatus.RUNNING);
        return workflow;
    }

    private static TaskEntity task(final String id) {
        var task = new TaskEntity();
        task.setId(id);
        return task;
    }

    private static TaskAttemptEntity attempt(final String id, final TaskAttemptStatus status) {
        var attempt = new TaskAttemptEntity();
        attempt.setId(id);
        attempt.setStatus(status);
        return attempt;
    }

    private void noVerdictFor(final String attemptId) {
        when(this.taskUpdateRepository
                .findFirstByTaskAttemptIdAndTypeInOrderByCreatedAtDesc(
                    eq(attemptId), anyList()))
            .thenReturn(Optional.empty());
    }

    private void verdictFor(final String attemptId, final TaskUpdateType type) {
        var update = new TaskUpdateEntity();
        update.setType(type);
        when(this.taskUpdateRepository
                .findFirstByTaskAttemptIdAndTypeInOrderByCreatedAtDesc(
                    eq(attemptId), anyList()))
            .thenReturn(Optional.of(update));
    }

    @Test
    void evaluate_withNoAttemptYet_dispatchesTheFirstAttempt() {
        var task = task("t1");
        var workflow = runningWorkflowWithOneTask(task);
        when(this.workflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.of(workflow));
        when(this.taskAttemptRepository.findFirstByTaskIdOrderByAttemptNumberDesc("t1"))
            .thenReturn(Optional.empty());
        when(this.taskAttemptRepository.save(any())).thenAnswer(
            invocation -> invocation.getArgument(0));

        this.scheduler.evaluate(WORKFLOW_ID);

        var captor = ArgumentCaptor.forClass(TaskAttemptEntity.class);
        verify(this.taskAssignmentOutbox).enqueue(captor.capture());
        assertThat(captor.getValue().getAttemptNumber()).isEqualTo(1);
        assertThat(captor.getValue().getStatus()).isEqualTo(TaskAttemptStatus.PENDING);
    }

    @Test
    void evaluate_whileAwaitingTheLlm_keepsDrivingTheRestOfTheWorkflow() {
        // AWAITING_LLM is not terminal: one task's review waiting out an outage must not stop
        // everything else in the workflow from being dispatched.
        var task = task("t1");
        var workflow = runningWorkflowWithOneTask(task);
        workflow.setStatus(WorkflowStatus.AWAITING_LLM);
        when(this.workflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.of(workflow));
        when(this.taskAttemptRepository.findFirstByTaskIdOrderByAttemptNumberDesc("t1"))
            .thenReturn(Optional.empty());
        when(this.taskAttemptRepository.save(any())).thenAnswer(
            invocation -> invocation.getArgument(0));

        this.scheduler.evaluate(WORKFLOW_ID);

        verify(this.taskAssignmentOutbox).enqueue(any());
        assertThat(workflow.getStatus()).isEqualTo(WorkflowStatus.AWAITING_LLM);
    }

    @Test
    void evaluate_forTerminalWorkflow_doesNothing() {
        var task = task("t1");
        var workflow = runningWorkflowWithOneTask(task);
        workflow.setStatus(WorkflowStatus.FAILED);
        when(this.workflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.of(workflow));

        this.scheduler.evaluate(WORKFLOW_ID);

        verify(this.taskAssignmentOutbox, never()).enqueue(any());
        verify(this.taskAttemptReviewOutbox, never()).enqueue(any());
    }

    @Test
    void evaluate_whenAttemptIsStillOpen_doesNotCompleteDispatchOrEnqueueReview() {
        var task = task("t1");
        var workflow = runningWorkflowWithOneTask(task);
        when(this.workflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.of(workflow));
        var runningAttempt = attempt("a1", TaskAttemptStatus.RUNNING);
        when(this.taskAttemptRepository.findFirstByTaskIdOrderByAttemptNumberDesc("t1"))
            .thenReturn(Optional.of(runningAttempt));

        this.scheduler.evaluate(WORKFLOW_ID);

        assertThat(workflow.getStatus()).isEqualTo(WorkflowStatus.RUNNING);
        verify(this.workflowSynthesisService, never()).synthesize(any());
        verify(this.taskAttemptReviewOutbox, never()).enqueue(any());
        verify(this.taskAssignmentOutbox, never()).enqueue(any());
    }

    @Test
    void evaluate_whenAttemptIsTerminalWithNoVerdictYet_enqueuesReview() {
        var task = task("t1");
        var workflow = runningWorkflowWithOneTask(task);
        when(this.workflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.of(workflow));
        var settledAttempt = attempt("a1", TaskAttemptStatus.SUCCEEDED);
        when(this.taskAttemptRepository.findFirstByTaskIdOrderByAttemptNumberDesc("t1"))
            .thenReturn(Optional.of(settledAttempt));
        this.noVerdictFor("a1");
        when(this.taskAttemptReviewOutboxMessageRepository
                .existsByTaskAttemptIdAndStatusIn(anyString(), anyList()))
            .thenReturn(false);

        this.scheduler.evaluate(WORKFLOW_ID);

        verify(this.taskAttemptReviewOutbox).enqueue(settledAttempt);
        assertThat(workflow.getStatus()).isEqualTo(WorkflowStatus.RUNNING);
    }

    @Test
    void evaluate_whenReviewIsAlreadyOpenForTheAttempt_doesNotEnqueueAnotherOne() {
        var task = task("t1");
        var workflow = runningWorkflowWithOneTask(task);
        when(this.workflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.of(workflow));
        var settledAttempt = attempt("a1", TaskAttemptStatus.FAILED);
        when(this.taskAttemptRepository.findFirstByTaskIdOrderByAttemptNumberDesc("t1"))
            .thenReturn(Optional.of(settledAttempt));
        this.noVerdictFor("a1");
        when(this.taskAttemptReviewOutboxMessageRepository
                .existsByTaskAttemptIdAndStatusIn(anyString(), anyList()))
            .thenReturn(true);

        this.scheduler.evaluate(WORKFLOW_ID);

        verify(this.taskAttemptReviewOutbox, never()).enqueue(any());
    }

    @Test
    void evaluate_whenTheOnlyTaskIsVerdictedCompleted_completesTheWorkflow() {
        var task = task("t1");
        var workflow = runningWorkflowWithOneTask(task);
        when(this.workflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.of(workflow));
        var succeededAttempt = attempt("a1", TaskAttemptStatus.SUCCEEDED);
        when(this.taskAttemptRepository.findFirstByTaskIdOrderByAttemptNumberDesc("t1"))
            .thenReturn(Optional.of(succeededAttempt));
        this.verdictFor("a1", TaskUpdateType.COMPLETED);
        when(this.workflowSynthesisService.synthesize(workflow)).thenReturn("Final answer.");

        this.scheduler.evaluate(WORKFLOW_ID);

        assertThat(workflow.getStatus()).isEqualTo(WorkflowStatus.SUCCEEDED);
        assertThat(workflow.getResult()).isEqualTo("Final answer.");
        assertThat(workflow.getCompletedAt()).isNotNull();
        verify(this.workflowRepository).save(workflow);
    }

    @Test
    void evaluate_whenTheOnlyTaskIsVerdictedFailedWithNoRetry_completesTheWorkflowAsFailed() {
        var task = task("t1");
        var workflow = runningWorkflowWithOneTask(task);
        when(this.workflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.of(workflow));
        var failedAttempt = attempt("a1", TaskAttemptStatus.FAILED);
        when(this.taskAttemptRepository.findFirstByTaskIdOrderByAttemptNumberDesc("t1"))
            .thenReturn(Optional.of(failedAttempt));
        this.verdictFor("a1", TaskUpdateType.FAILED);
        when(this.workflowSynthesisService.synthesize(workflow)).thenReturn("Partial answer.");

        this.scheduler.evaluate(WORKFLOW_ID);

        assertThat(workflow.getStatus()).isEqualTo(WorkflowStatus.FAILED);
        verify(this.taskAssignmentOutbox, never()).enqueue(any());
    }

    @Test
    void dispatch_createsPendingAttemptAndEnqueuesIt() {
        var task = task("t1");
        when(this.taskAttemptRepository.save(any())).thenAnswer(
            invocation -> invocation.getArgument(0));

        this.scheduler.dispatch(task, 2);

        var captor = ArgumentCaptor.forClass(TaskAttemptEntity.class);
        verify(this.taskAssignmentOutbox).enqueue(captor.capture());
        assertThat(captor.getValue().getTask()).isSameAs(task);
        assertThat(captor.getValue().getAttemptNumber()).isEqualTo(2);
        assertThat(captor.getValue().getStatus()).isEqualTo(TaskAttemptStatus.PENDING);
    }
}
