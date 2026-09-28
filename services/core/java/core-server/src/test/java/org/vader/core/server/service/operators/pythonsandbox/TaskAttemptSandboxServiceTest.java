package org.vader.core.server.service.operators.pythonsandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.vader.common.model.vader.entity.ClientPromptEntity;
import org.vader.common.model.vader.entity.ObjectMetadataEntity;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.common.model.vader.entity.TaskGraphEntity;
import org.vader.common.model.vader.entity.TaskPlanEntity;
import org.vader.common.model.vader.entity.WorkflowEntity;
import org.vader.core.server.exceptions.SandboxExecutionException;
import org.vader.core.server.exceptions.UnknownAssignmentException;
import org.vader.core.server.models.SandboxExecutionRequest;
import org.vader.core.server.models.SandboxExecutionResult;
import org.vader.core.server.models.SandboxInfo;
import org.vader.core.server.repository.TaskAttemptRepository;

@ExtendWith(MockitoExtension.class)
class TaskAttemptSandboxServiceTest {

    @Mock
    private PythonSandboxService sandboxService;

    @Mock
    private TaskAttemptRepository taskAttemptRepository;

    @InjectMocks
    private TaskAttemptSandboxService service;

    private String attemptId;
    private String sandboxName;
    private ClientPromptEntity clientPrompt;

    @BeforeEach
    void setUp() {
        this.attemptId = UUID.randomUUID().toString();
        this.sandboxName = TaskAttemptSandboxNaming.resolve(this.attemptId);
        this.clientPrompt = new ClientPromptEntity();
        // Lenient: delete() never looks the attempt up.
        lenient().when(this.taskAttemptRepository.findById(this.attemptId))
            .thenReturn(Optional.of(attemptFor(this.attemptId, this.clientPrompt)));
    }

    private static TaskAttemptEntity attemptFor(
            final String attemptId, final ClientPromptEntity clientPrompt) {
        var workflow = new WorkflowEntity();
        workflow.setClientPrompt(clientPrompt);
        var taskPlan = new TaskPlanEntity();
        taskPlan.setWorkflow(workflow);
        var taskGraph = new TaskGraphEntity();
        taskGraph.setTaskPlan(taskPlan);
        var task = new TaskEntity();
        task.setTaskGraph(taskGraph);
        var attempt = new TaskAttemptEntity();
        attempt.setId(attemptId);
        attempt.setTask(task);
        return attempt;
    }

    private static ObjectMetadataEntity attachedFile(final String originalFilename) {
        var file = new ObjectMetadataEntity();
        file.setId(UUID.randomUUID().toString());
        file.setOriginalFilename(originalFilename);
        return file;
    }

    private static SandboxInfo sandboxIn(final String name, final String phase) {
        return new SandboxInfo(name, "vader", phase, name + ".vader.svc.cluster.local");
    }

    @Test
    void runCode_provisionsTheAttemptsOwnSandboxThenRunsInIt() {
        var expected = new SandboxExecutionResult("2\n", "", 0, false);
        when(this.sandboxService.ensureReady(this.sandboxName))
            .thenReturn(sandboxIn(this.sandboxName, "Running"));
        when(this.sandboxService.runCode(
            this.sandboxName, new SandboxExecutionRequest("print(1 + 1)", null, null)))
            .thenReturn(expected);

        var result = this.service.runCode(this.attemptId, "print(1 + 1)");

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void runCode_stagesEveryAttachedFileBeforeRunningTheCode() {
        var file = attachedFile("EP_Tactics.xlsx");
        this.clientPrompt.setFiles(Set.of(file));
        when(this.sandboxService.ensureReady(this.sandboxName))
            .thenReturn(sandboxIn(this.sandboxName, "Running"));

        this.service.runCode(this.attemptId, "pass");

        var order = inOrder(this.sandboxService);
        order.verify(this.sandboxService).ensureReady(this.sandboxName);
        order.verify(this.sandboxService)
            .stageObjectIfAbsent(this.sandboxName, file.getId(), "EP_Tactics.xlsx");
        order.verify(this.sandboxService).runCode(anyString(), any());
    }

    @Test
    void runCode_whenTheSandboxNeverBecomesReady_failsWithoutStagingOrRunning() {
        this.clientPrompt.setFiles(Set.of(attachedFile("EP_Tactics.xlsx")));
        when(this.sandboxService.ensureReady(this.sandboxName))
            .thenReturn(sandboxIn(this.sandboxName, "Pending"));

        assertThatThrownBy(() -> this.service.runCode(this.attemptId, "pass"))
            .isInstanceOf(SandboxExecutionException.class)
            .hasMessageContaining("did not become ready")
            .hasMessageContaining("Pending");
        verify(this.sandboxService, never()).stageObjectIfAbsent(anyString(), any(), any());
        verify(this.sandboxService, never()).runCode(anyString(), any());
    }

    @Test
    void runCode_forAnUnknownAttempt_throwsUnknownAssignment() {
        var unknownId = UUID.randomUUID().toString();
        when(this.taskAttemptRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> this.service.runCode(unknownId, "pass"))
            .isInstanceOf(UnknownAssignmentException.class);
    }

    @Test
    void delete_deletesTheSandboxNamedForTheAttempt() {
        this.service.delete(this.attemptId);

        verify(this.sandboxService).delete(this.sandboxName);
    }
}
