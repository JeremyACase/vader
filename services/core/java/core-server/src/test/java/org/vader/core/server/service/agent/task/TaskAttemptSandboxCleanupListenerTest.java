package org.vader.core.server.service.agent.task;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.core.server.models.TaskAttemptSettledEvent;
import org.vader.core.server.service.operators.pythonsandbox.TaskAttemptSandboxService;

class TaskAttemptSandboxCleanupListenerTest {

    private TaskAttemptSandboxService sandboxService;
    private TaskAttemptSandboxCleanupListener listener;

    @BeforeEach
    void setUp() {
        this.sandboxService = mock(TaskAttemptSandboxService.class);
        this.listener = new TaskAttemptSandboxCleanupListener();
        ReflectionTestUtils.setField(this.listener, "sandboxService", this.sandboxService);
        // A synchronous executor keeps the test deterministic without racing a real thread pool.
        ReflectionTestUtils.setField(this.listener, "executor", new SyncTaskExecutor());
    }

    @Test
    void onTaskAttemptSettled_deletesTheSettledAttemptsOwnSandbox() {
        var assignmentId = UUID.randomUUID().toString();

        this.listener.onTaskAttemptSettled(new TaskAttemptSettledEvent("workflow-1", assignmentId));

        verify(this.sandboxService).delete(assignmentId);
    }

    @Test
    void onTaskAttemptSettled_swallowsDeletionFailureRatherThanPropagating() {
        doThrow(new RuntimeException("api server unreachable"))
            .when(this.sandboxService).delete(any());

        this.listener.onTaskAttemptSettled(
            new TaskAttemptSettledEvent("workflow-1", UUID.randomUUID().toString()));

        verify(this.sandboxService).delete(any());
    }
}
