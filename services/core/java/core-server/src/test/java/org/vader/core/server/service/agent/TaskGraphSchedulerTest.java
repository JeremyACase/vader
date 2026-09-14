package org.vader.core.server.service.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
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
import org.vader.core.server.repository.TaskAttemptRepository;
import org.vader.core.server.repository.WorkflowRepository;
import org.vader.core.server.service.io.TaskAssignmentOutbox;

class TaskGraphSchedulerTest {

    private static final String WORKFLOW_ID = "wwwwwwww-1111-2222-3333-444444444444";

    private WorkflowRepository workflowRepository;
    private TaskAttemptRepository taskAttemptRepository;
    private TaskAssignmentOutbox taskAssignmentOutbox;
    private WorkflowSynthesisService workflowSynthesisService;
    private TaskGraphScheduler scheduler;

    @BeforeEach
    void setUp() {
        this.workflowRepository = mock(WorkflowRepository.class);
        this.taskAttemptRepository = mock(TaskAttemptRepository.class);
        this.taskAssignmentOutbox = mock(TaskAssignmentOutbox.class);
        this.workflowSynthesisService = mock(WorkflowSynthesisService.class);

        this.scheduler = new TaskGraphScheduler();
        ReflectionTestUtils.setField(
            this.scheduler, "workflowRepository", this.workflowRepository);
        ReflectionTestUtils.setField(
            this.scheduler, "taskAttemptRepository", this.taskAttemptRepository);
        ReflectionTestUtils.setField(
            this.scheduler, "taskAssignmentOutbox", this.taskAssignmentOutbox);
        ReflectionTestUtils.setField(
            this.scheduler, "workflowSynthesisService", this.workflowSynthesisService);
        ReflectionTestUtils.setField(this.scheduler, "maxAttemptsPerTask", 3);
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

    @Test
    void evaluate_whenTheOnlyTaskSucceeded_completesTheWorkflowWithTheSynthesizedResult() {
        var task = task("t1");
        var workflow = runningWorkflowWithOneTask(task);
        when(this.workflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.of(workflow));
        var succeededAttempt = new TaskAttemptEntity();
        succeededAttempt.setStatus(TaskAttemptStatus.SUCCEEDED);
        when(this.taskAttemptRepository.findFirstByTaskIdOrderByAttemptNumberDesc("t1"))
            .thenReturn(Optional.of(succeededAttempt));
        when(this.workflowSynthesisService.synthesize(workflow)).thenReturn("Final answer.");

        this.scheduler.evaluate(WORKFLOW_ID);

        assertThat(workflow.getStatus()).isEqualTo(WorkflowStatus.SUCCEEDED);
        assertThat(workflow.getResult()).isEqualTo("Final answer.");
        assertThat(workflow.getCompletedAt()).isNotNull();
        verify(this.workflowRepository).save(workflow);
    }

    @Test
    void evaluate_whenTaskIsStillRunning_doesNotCompleteOrSynthesizeYet() {
        var task = task("t1");
        var workflow = runningWorkflowWithOneTask(task);
        when(this.workflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.of(workflow));
        var runningAttempt = new TaskAttemptEntity();
        runningAttempt.setStatus(TaskAttemptStatus.RUNNING);
        when(this.taskAttemptRepository.findFirstByTaskIdOrderByAttemptNumberDesc("t1"))
            .thenReturn(Optional.of(runningAttempt));

        this.scheduler.evaluate(WORKFLOW_ID);

        assertThat(workflow.getStatus()).isEqualTo(WorkflowStatus.RUNNING);
        assertThat(workflow.getResult()).isNull();
        verify(this.workflowSynthesisService, never()).synthesize(any());
    }
}
