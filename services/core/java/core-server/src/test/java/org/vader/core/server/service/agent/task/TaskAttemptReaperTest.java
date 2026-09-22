package org.vader.core.server.service.agent.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskAttemptStatus;
import org.vader.core.server.models.ResultRequest;
import org.vader.core.server.repository.TaskAttemptRepository;

class TaskAttemptReaperTest {

    private static final long DEADLINE_SECONDS = 600L;
    private static final long GRACE_PERIOD_SECONDS = 300L;

    private TaskAttemptRepository taskAttemptRepository;
    private TaskAgentService taskAttemptService;
    private TaskAttemptReaper reaper;

    @BeforeEach
    void setUp() {
        this.taskAttemptRepository = mock(TaskAttemptRepository.class);
        this.taskAttemptService = mock(TaskAgentService.class);

        this.reaper = new TaskAttemptReaper();
        ReflectionTestUtils.setField(
            this.reaper, "taskAttemptRepository", this.taskAttemptRepository);
        ReflectionTestUtils.setField(this.reaper, "taskAttemptService", this.taskAttemptService);
        ReflectionTestUtils.setField(this.reaper, "deadlineSeconds", DEADLINE_SECONDS);
        ReflectionTestUtils.setField(this.reaper, "gracePeriodSeconds", GRACE_PERIOD_SECONDS);
    }

    private static TaskAttemptEntity attempt(final String id, final TaskAttemptStatus status) {
        var attempt = new TaskAttemptEntity();
        attempt.setId(id);
        attempt.setStatus(status);
        return attempt;
    }

    @Test
    void reap_doesNothingWhenNoAttemptsAreStale() {
        when(this.taskAttemptRepository.findStale(anyList(), any())).thenReturn(List.of());

        this.reaper.reap();

        verify(this.taskAttemptService, never()).submitResult(any(), any());
    }

    @Test
    void reap_queriesWithCutoffOfDeadlinePlusGracePeriodBeforeNow() {
        when(this.taskAttemptRepository.findStale(anyList(), any())).thenReturn(List.of());
        var before = OffsetDateTime.now().minusSeconds(DEADLINE_SECONDS + GRACE_PERIOD_SECONDS);

        this.reaper.reap();

        var captor = ArgumentCaptor.forClass(OffsetDateTime.class);
        var openStatuses = List.of(
            TaskAttemptStatus.PENDING, TaskAttemptStatus.DISPATCHED, TaskAttemptStatus.RUNNING);
        verify(this.taskAttemptRepository).findStale(eq(openStatuses), captor.capture());

        var after = OffsetDateTime.now().minusSeconds(DEADLINE_SECONDS + GRACE_PERIOD_SECONDS);
        assertThat(captor.getValue()).isBetween(before.minus(1, ChronoUnit.SECONDS), after);
    }

    @Test
    void reap_settlesEachStaleAttemptAsTimedOut() {
        var attempt = attempt("aaaaaaaa-1111-2222-3333-444444444444", TaskAttemptStatus.RUNNING);
        when(this.taskAttemptRepository.findStale(anyList(), any())).thenReturn(List.of(attempt));

        this.reaper.reap();

        var captor = ArgumentCaptor.forClass(ResultRequest.class);
        verify(this.taskAttemptService).submitResult(eq(attempt.getId()), captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(TaskAttemptStatus.TIMED_OUT);
        assertThat(captor.getValue().failureReason()).contains("Reaped");
    }

    @Test
    void reap_continuesTheBatchWhenOneAttemptFailsToSettle() {
        var first = attempt("aaaaaaaa-1111-2222-3333-444444444444", TaskAttemptStatus.DISPATCHED);
        var second = attempt("bbbbbbbb-1111-2222-3333-444444444444", TaskAttemptStatus.RUNNING);
        when(this.taskAttemptRepository.findStale(anyList(), any()))
            .thenReturn(List.of(first, second));
        doThrow(new RuntimeException("already settled"))
            .when(this.taskAttemptService).submitResult(eq(first.getId()), any());

        this.reaper.reap();

        verify(this.taskAttemptService, times(2)).submitResult(any(), any());
        verify(this.taskAttemptService).submitResult(eq(second.getId()), any());
    }
}
