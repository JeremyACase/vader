package org.vader.core.server.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.transaction.PlatformTransactionManager;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.core.server.sandbox.model.SandboxFileInfo;
import org.vader.core.server.sandbox.model.WorkspaceFile;
import org.vader.core.server.storage.ObjectContent;
import org.vader.core.server.storage.ObjectDescriptor;
import org.vader.core.server.storage.ObjectStorageService;
import org.vader.core.server.taskagent.AssignmentAlreadyTerminalException;
import org.vader.core.server.taskagent.TaskAttemptLifecycleService;

@ExtendWith(MockitoExtension.class)
class TaskAttemptOutputCollectionServiceTest {

    private static final String CONTENT = "month,revenue\n2024-01,40000\n";

    @Mock
    private PythonSandboxService sandboxService;

    @Mock
    private TaskAttemptSandboxService taskAttemptSandboxService;

    @Mock
    private TaskAttemptLifecycleService lifecycleService;

    @Mock
    private TaskWorkspaceFileResolver taskWorkspaceFileResolver;

    @Mock
    private ObjectStorageService objectStorageService;

    @Mock
    private PlatformTransactionManager transactionManager;

    @InjectMocks
    private TaskAttemptOutputCollectionService service;

    private String attemptId;
    private String sandboxName;
    private TaskEntity task;

    @BeforeEach
    void setUp() {
        this.attemptId = UUID.randomUUID().toString();
        this.sandboxName = TaskAttemptSandboxNaming.resolve(this.attemptId);
        this.task = new TaskEntity();
        this.service.init();
    }

    private void openAttemptWith(
            final List<WorkspaceFile> staged, final List<ObjectDescriptor> uploaded) {
        var attempt = new TaskAttemptEntity();
        attempt.setId(this.attemptId);
        attempt.setTask(this.task);
        when(this.lifecycleService.requireOpen(this.attemptId)).thenReturn(attempt);
        when(this.taskWorkspaceFileResolver.filesFor(this.task)).thenReturn(staged);
        when(this.objectStorageService.describeTaskAttemptOutputs(this.attemptId))
            .thenReturn(uploaded);
    }

    private void storedObject(final String id, final String filename, final String content) {
        var bytes = content.getBytes(StandardCharsets.UTF_8);
        when(this.objectStorageService.retrieve(id)).thenReturn(new ObjectContent(
            new ByteArrayResource(bytes), filename, "text/csv", bytes.length));
    }

    private static ObjectDescriptor descriptor(
            final String id, final String filename, final String content) {
        return new ObjectDescriptor(id, filename, "text/csv", sizeOf(content));
    }

    private static SandboxFileInfo listed(final String filename, final String content) {
        return new SandboxFileInfo(filename, sizeOf(content), sha256(content));
    }

    private static long sizeOf(final String content) {
        return content.getBytes(StandardCharsets.UTF_8).length;
    }

    private static String sha256(final String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void collect_savesFilesTheAgentWroteButNeverUploaded() {
        openAttemptWith(List.of(), List.of());
        when(this.sandboxService.listFiles(this.sandboxName))
            .thenReturn(List.of(listed("monthly_revenue.csv", CONTENT)));
        var saved = descriptor("saved-id", "monthly_revenue.csv", CONTENT);
        when(this.taskAttemptSandboxService.uploadFile(this.attemptId, "monthly_revenue.csv"))
            .thenReturn(saved);

        assertThat(this.service.collect(this.attemptId)).containsExactly(saved);
    }

    @Test
    void collect_leavesStagedFilesTheAgentDidNotChange() {
        var staged = new WorkspaceFile("input-id", "quarterly_revenue.csv", "text/csv", null);
        openAttemptWith(List.of(staged), List.of());
        when(this.objectStorageService.describe("input-id"))
            .thenReturn(descriptor("input-id", "quarterly_revenue.csv", CONTENT));
        storedObject("input-id", "quarterly_revenue.csv", CONTENT);
        when(this.sandboxService.listFiles(this.sandboxName))
            .thenReturn(List.of(listed("quarterly_revenue.csv", CONTENT)));

        assertThat(this.service.collect(this.attemptId)).isEmpty();
        verify(this.taskAttemptSandboxService, never()).uploadFile(anyString(), anyString());
    }

    @Test
    void collect_savesStagedFilesTheAgentRewrote() {
        var staged = new WorkspaceFile("input-id", "quarterly_revenue.csv", "text/csv", null);
        openAttemptWith(List.of(staged), List.of());
        when(this.objectStorageService.describe("input-id"))
            .thenReturn(descriptor("input-id", "quarterly_revenue.csv", CONTENT));
        storedObject("input-id", "quarterly_revenue.csv", CONTENT);
        var rewritten = CONTENT.replace("40000", "50000");
        when(this.sandboxService.listFiles(this.sandboxName))
            .thenReturn(List.of(listed("quarterly_revenue.csv", rewritten)));

        this.service.collect(this.attemptId);

        verify(this.taskAttemptSandboxService).uploadFile(this.attemptId, "quarterly_revenue.csv");
    }

    @Test
    void collect_leavesFilesTheAgentAlreadyUploadedUnchanged() {
        openAttemptWith(
            List.of(), List.of(descriptor("uploaded-id", "monthly_revenue.csv", CONTENT)));
        storedObject("uploaded-id", "monthly_revenue.csv", CONTENT);
        when(this.sandboxService.listFiles(this.sandboxName))
            .thenReturn(List.of(listed("monthly_revenue.csv", CONTENT)));

        assertThat(this.service.collect(this.attemptId)).isEmpty();
        verify(this.taskAttemptSandboxService, never()).uploadFile(anyString(), anyString());
    }

    @Test
    void collect_whenTheSandboxCannotBeListed_savesNothingAndDoesNotFail() {
        openAttemptWith(List.of(), List.of());
        when(this.sandboxService.listFiles(this.sandboxName))
            .thenThrow(new SandboxExecutionException("unreachable"));

        assertThat(this.service.collect(this.attemptId)).isEmpty();
    }

    @Test
    void collect_forSettledAttempts_refusesWithoutTouchingTheSandbox() {
        when(this.lifecycleService.requireOpen(this.attemptId))
            .thenThrow(new AssignmentAlreadyTerminalException("settled"));

        assertThatThrownBy(() -> this.service.collect(this.attemptId))
            .isInstanceOf(AssignmentAlreadyTerminalException.class);
        verifyNoInteractions(this.sandboxService);
        verify(this.taskAttemptSandboxService, never()).uploadFile(any(), any());
    }
}
