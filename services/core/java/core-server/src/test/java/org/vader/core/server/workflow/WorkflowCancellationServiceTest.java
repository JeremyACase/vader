package org.vader.core.server.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskAttemptStatus;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.common.model.vader.entity.TaskGraphEntity;
import org.vader.common.model.vader.entity.TaskPlanEntity;
import org.vader.common.model.vader.entity.WorkflowEntity;
import org.vader.common.model.vader.entity.WorkflowStatus;
import org.vader.core.server.taskagent.TaskAttemptLifecycleService;

class WorkflowCancellationServiceTest {

    private static final String WORKFLOW_ID = "wwwwwwww-1111-2222-3333-444444444444";

    private final Map<String, TaskAttemptEntity> latestAttempts = new HashMap<>();
    private WorkflowRepository workflowRepository;
    private TaskAttemptLifecycleService lifecycleService;
    private WorkflowCancellationService service;
    private WorkflowEntity workflow;
    private TaskGraphEntity taskGraph;

    @BeforeEach
    void setUp() {
        this.workflowRepository = mock(WorkflowRepository.class);
        this.lifecycleService = mock(TaskAttemptLifecycleService.class);
        var taskAttemptRepository = mock(TaskAttemptRepository.class);
        when(taskAttemptRepository.findFirstByTaskIdOrderByAttemptNumberDesc(anyString()))
            .thenAnswer(call -> Optional.ofNullable(this.latestAttempts.get(call.getArgument(0))));

        this.service = new WorkflowCancellationService();
        ReflectionTestUtils.setField(this.service, "workflowRepository", this.workflowRepository);
        ReflectionTestUtils.setField(this.service, "taskAttemptRepository", taskAttemptRepository);
        ReflectionTestUtils.setField(
            this.service, "taskAttemptLifecycleService", this.lifecycleService);

        this.workflow = new WorkflowEntity();
        this.workflow.setId(WORKFLOW_ID);
        var taskPlan = new TaskPlanEntity();
        taskPlan.setWorkflow(this.workflow);
        this.taskGraph = new TaskGraphEntity();
        this.taskGraph.setTaskPlan(taskPlan);
        taskPlan.setTaskGraph(this.taskGraph);
        this.workflow.setTaskPlan(taskPlan);
        when(this.workflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.of(this.workflow));
    }

    private TaskEntity planned() {
        var task = new TaskEntity();
        task.setId(UUID.randomUUID().toString());
        task.setTaskGraph(this.taskGraph);
        this.taskGraph.getTasks().add(task);
        return task;
    }

    private TaskAttemptEntity latestAttempt(final TaskEntity task, final TaskAttemptStatus status) {
        var attempt = new TaskAttemptEntity();
        attempt.setId(UUID.randomUUID().toString());
        attempt.setTask(task);
        attempt.setStatus(status);
        this.latestAttempts.put(task.getId(), attempt);
        return attempt;
    }

    @Test
    void cancel_marksRunningWorkflowCancelledAndStopsEveryOpenAttempt() {
        var parent = planned();
        latestAttempt(parent, TaskAttemptStatus.SUCCEEDED);
        var subtask = new TaskEntity();
        subtask.setId(UUID.randomUUID().toString());
        subtask.setParentTask(parent);
        parent.getSubTasks().add(subtask);
        planned();
        final var pending = latestAttempt(subtask, TaskAttemptStatus.PENDING);
        final var running = latestAttempt(planned(), TaskAttemptStatus.RUNNING);

        var outcome = this.service.cancel(WORKFLOW_ID);

        assertThat(outcome.status()).isEqualTo(WorkflowStatus.CANCELLED);
        assertThat(outcome.cancelledAttempts()).isEqualTo(2);
        assertThat(this.workflow.getStatus()).isEqualTo(WorkflowStatus.CANCELLED);
        assertThat(this.workflow.getCompletedAt()).isNotNull();
        verify(this.workflowRepository).save(this.workflow);
        verify(this.lifecycleService).cancel(running.getId(),
            "Cancelled: the workflow was cancelled.");
        verify(this.lifecycleService).cancel(pending.getId(),
            "Cancelled: the workflow was cancelled.");
    }

    @Test
    void cancel_alsoStopsWorkflowWaitingOnTheLlm() {
        this.workflow.setStatus(WorkflowStatus.AWAITING_LLM);

        var outcome = this.service.cancel(WORKFLOW_ID);

        assertThat(outcome.status()).isEqualTo(WorkflowStatus.CANCELLED);
    }

    @Test
    void cancel_leavesSettledWorkflowAsItIs() {
        this.workflow.setStatus(WorkflowStatus.SUCCEEDED);
        latestAttempt(planned(), TaskAttemptStatus.RUNNING);

        var outcome = this.service.cancel(WORKFLOW_ID);

        assertThat(outcome.status()).isEqualTo(WorkflowStatus.SUCCEEDED);
        assertThat(outcome.cancelledAttempts()).isZero();
        verify(this.workflowRepository, never()).save(any());
        verify(this.lifecycleService, never()).cancel(anyString(), anyString());
    }

    @Test
    void cancel_refusesAnUnknownWorkflow() {
        assertThatThrownBy(() -> this.service.cancel("nope"))
            .isInstanceOf(UnknownWorkflowException.class);
    }
}
