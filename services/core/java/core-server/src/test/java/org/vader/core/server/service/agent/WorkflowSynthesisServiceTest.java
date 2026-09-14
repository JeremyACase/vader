package org.vader.core.server.service.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.model.vader.entity.ClientPromptEntity;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskAttemptStatus;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.common.model.vader.entity.TaskGraphEntity;
import org.vader.common.model.vader.entity.TaskPlanEntity;
import org.vader.common.model.vader.entity.WorkflowEntity;
import org.vader.core.server.models.WorkflowSynthesisRequest;
import org.vader.core.server.repository.TaskAttemptRepository;
import org.vader.core.server.service.strategies.synthesis.interfaces.InterfaceWorkflowSynthesisStrategy;

class WorkflowSynthesisServiceTest {

    private TaskAttemptRepository taskAttemptRepository;
    private InterfaceWorkflowSynthesisStrategy synthesisStrategy;
    private WorkflowSynthesisService service;

    @BeforeEach
    void setUp() {
        this.taskAttemptRepository = mock(TaskAttemptRepository.class);
        this.synthesisStrategy = mock(InterfaceWorkflowSynthesisStrategy.class);

        this.service = new WorkflowSynthesisService();
        ReflectionTestUtils.setField(
            this.service, "taskAttemptRepository", this.taskAttemptRepository);
        ReflectionTestUtils.setField(this.service, "synthesisStrategy", this.synthesisStrategy);
    }

    private static WorkflowEntity workflowWithTasks(final TaskEntity... tasks) {
        var clientPrompt = new ClientPromptEntity();
        clientPrompt.setText("What does this spreadsheet show?");
        var taskGraph = new TaskGraphEntity();
        taskGraph.setTasks(Set.of(tasks));
        var taskPlan = new TaskPlanEntity();
        taskPlan.setObjective("Analyze the spreadsheet");
        taskPlan.setTaskGraph(taskGraph);
        var workflow = new WorkflowEntity();
        workflow.setClientPrompt(clientPrompt);
        workflow.setTaskPlan(taskPlan);
        return workflow;
    }

    private static TaskEntity task(final String id, final String title) {
        var task = new TaskEntity();
        task.setId(id);
        task.setTitle(title);
        return task;
    }

    private static TaskAttemptEntity attempt(final TaskAttemptStatus status, final String output) {
        var attempt = new TaskAttemptEntity();
        attempt.setStatus(status);
        if (status == TaskAttemptStatus.SUCCEEDED) {
            attempt.setResult(output);
        } else {
            attempt.setFailureReason(output);
        }
        return attempt;
    }

    @Test
    void synthesize_buildsRequestFromEveryTasksLatestAttempt() {
        var succeededTask = task("t1", "Read the file");
        var failedTask = task("t2", "Chart the trend");
        when(this.taskAttemptRepository.findFirstByTaskIdOrderByAttemptNumberDesc("t1"))
            .thenReturn(Optional.of(attempt(TaskAttemptStatus.SUCCEEDED, "Found 3 sheets.")));
        when(this.taskAttemptRepository.findFirstByTaskIdOrderByAttemptNumberDesc("t2"))
            .thenReturn(Optional.of(attempt(TaskAttemptStatus.FAILED, "No charting library.")));
        when(this.synthesisStrategy.synthesize(any())).thenReturn("Final answer.");

        var result = this.service.synthesize(workflowWithTasks(succeededTask, failedTask));

        assertThat(result).isEqualTo("Final answer.");
        var captor = ArgumentCaptor.forClass(WorkflowSynthesisRequest.class);
        verify(this.synthesisStrategy).synthesize(captor.capture());
        assertThat(captor.getValue().promptText()).isEqualTo("What does this spreadsheet show?");
        assertThat(captor.getValue().objective()).isEqualTo("Analyze the spreadsheet");
        assertThat(captor.getValue().taskOutcomes()).hasSize(2);
        assertThat(captor.getValue().taskOutcomes())
            .anySatisfy(outcome -> {
                assertThat(outcome.title()).isEqualTo("Read the file");
                assertThat(outcome.succeeded()).isTrue();
                assertThat(outcome.output()).isEqualTo("Found 3 sheets.");
            })
            .anySatisfy(outcome -> {
                assertThat(outcome.title()).isEqualTo("Chart the trend");
                assertThat(outcome.succeeded()).isFalse();
                assertThat(outcome.output()).isEqualTo("No charting library.");
            });
    }

    @Test
    void synthesize_treatsNeverAttemptedTaskAsFailed() {
        var task = task("t1", "Untouched task");
        when(this.taskAttemptRepository.findFirstByTaskIdOrderByAttemptNumberDesc("t1"))
            .thenReturn(Optional.empty());
        when(this.synthesisStrategy.synthesize(any())).thenReturn("Final answer.");

        this.service.synthesize(workflowWithTasks(task));

        var captor = ArgumentCaptor.forClass(WorkflowSynthesisRequest.class);
        verify(this.synthesisStrategy).synthesize(captor.capture());
        assertThat(captor.getValue().taskOutcomes()).hasSize(1);
        assertThat(captor.getValue().taskOutcomes().get(0).succeeded()).isFalse();
    }

    @Test
    void synthesize_whenStrategyFails_fallsBackToDerivedSummaryInsteadOfThrowing() {
        var succeededTask = task("t1", "Read the file");
        when(this.taskAttemptRepository.findFirstByTaskIdOrderByAttemptNumberDesc("t1"))
            .thenReturn(Optional.of(attempt(TaskAttemptStatus.SUCCEEDED, "Found 3 sheets.")));
        when(this.synthesisStrategy.synthesize(any()))
            .thenThrow(new RuntimeException("local LLM unreachable"));

        var result = this.service.synthesize(workflowWithTasks(succeededTask));

        assertThat(result).isEqualTo("Completed 1 of 1 tasks.");
    }
}
