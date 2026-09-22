package org.vader.core.server.service.agent.task;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.core.server.models.TaskAttemptSettledEvent;
import org.vader.core.server.service.operators.agentharness.AgentHarnessOperator;

class AgentHarnessJobCleanupListenerTest {

    private AgentHarnessOperator operator;
    private AgentHarnessJobCleanupListener listener;

    @BeforeEach
    void setUp() {
        this.operator = mock(AgentHarnessOperator.class);
        this.listener = new AgentHarnessJobCleanupListener();
        ReflectionTestUtils.setField(this.listener, "operator", this.operator);
        // A synchronous executor keeps the test deterministic without racing a real thread pool.
        ReflectionTestUtils.setField(this.listener, "executor", new SyncTaskExecutor());
    }

    @Test
    void onTaskAttemptSettled_deletesTheJobNamedForTheAssignment() {
        this.listener.onTaskAttemptSettled(
            new TaskAttemptSettledEvent("workflow-1", "aaaaaaaa-1111-2222-3333-444444444444"));

        verify(this.operator).delete("agent-harness-aaaaaaaa-1111-2222-3333-444444444444");
    }

    @Test
    void onTaskAttemptSettled_swallowsDeletionFailureRatherThanPropagating() {
        doThrow(new RuntimeException("api server unreachable")).when(this.operator).delete(any());

        this.listener.onTaskAttemptSettled(
            new TaskAttemptSettledEvent("workflow-1", "aaaaaaaa-1111-2222-3333-444444444444"));

        verify(this.operator).delete(any());
    }
}
