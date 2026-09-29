package org.vader.core.server.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.common.model.vader.entity.TaskUpdateAuthor;
import org.vader.common.model.vader.entity.TaskUpdateEntity;
import org.vader.common.model.vader.entity.TaskUpdateType;
import org.vader.core.server.llm.LlmRequestQueue;
import org.vader.core.server.review.model.ReattemptDecision;
import org.vader.core.server.review.model.ReattemptDecisionRequest;
import org.vader.core.server.workflow.TaskAttemptRepository;
import org.vader.core.server.workflow.TaskGraphScheduler;
import org.vader.core.server.workflow.TaskUpdateRepository;
import org.vader.core.server.workflow.TaskUpdateService;

class ReattemptDecisionServiceTest {

    private static final String ATTEMPT_ID = "bbbbbbbb-1111-2222-3333-444444444444";

    private TaskAttemptRepository taskAttemptRepository;
    private TaskUpdateRepository taskUpdateRepository;
    private TaskUpdateService taskUpdateService;
    private TaskGraphScheduler taskGraphScheduler;
    private LlmRequestQueue requestQueue;
    private ReattemptDecisionService service;

    @BeforeEach
    void setUp() {
        this.taskAttemptRepository = mock(TaskAttemptRepository.class);
        this.taskUpdateRepository = mock(TaskUpdateRepository.class);
        this.taskUpdateService = mock(TaskUpdateService.class);
        this.taskGraphScheduler = mock(TaskGraphScheduler.class);
        this.requestQueue = mock(LlmRequestQueue.class);

        this.service = new ReattemptDecisionService();
        ReflectionTestUtils.setField(
            this.service, "taskAttemptRepository", this.taskAttemptRepository);
        ReflectionTestUtils.setField(
            this.service, "taskUpdateRepository", this.taskUpdateRepository);
        ReflectionTestUtils.setField(this.service, "taskUpdateService", this.taskUpdateService);
        ReflectionTestUtils.setField(this.service, "taskGraphScheduler", this.taskGraphScheduler);
        ReflectionTestUtils.setField(this.service, "requestQueue", this.requestQueue);
        ReflectionTestUtils.setField(this.service, "maxAttemptsPerTask", 3);
    }

    private static TaskAttemptEntity attemptOf(final TaskEntity task, final int attemptNumber) {
        var attempt = new TaskAttemptEntity();
        attempt.setId(ATTEMPT_ID);
        attempt.setTask(task);
        attempt.setAttemptNumber(attemptNumber);
        return attempt;
    }

    @Test
    void decideReattempt_whenAttemptCapReached_givesUpWithoutConsultingTheLlm() {
        var task = new TaskEntity();
        task.setId("t1");
        var attempt = attemptOf(task, 3);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));

        this.service.decideReattempt(ATTEMPT_ID);

        verify(this.requestQueue, never())
            .submit(eq(ReattemptDecisionLlmExecutor.class), any());
        verify(this.taskGraphScheduler, never()).dispatch(any(), any(Integer.class));
        var descriptionCaptor = ArgumentCaptor.forClass(String.class);
        verify(this.taskUpdateService).record(
            same(task), same(attempt), eq(TaskUpdateType.UPDATE), descriptionCaptor.capture(),
            eq(TaskUpdateAuthor.ORCHESTRATOR));
        assertThat(descriptionCaptor.getValue()).contains("cap");
    }

    @Test
    void decideReattempt_whenTheLlmApproves_dispatchesTheNextAttempt() {
        var task = new TaskEntity();
        task.setId("t1");
        task.setTitle("title");
        task.setDescription("description");
        var attempt = attemptOf(task, 1);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        when(this.taskUpdateRepository.findFirstByTaskAttemptIdAndTypeInOrderByCreatedAtDesc(
                eq(ATTEMPT_ID), any()))
            .thenReturn(Optional.empty());
        when(this.taskUpdateRepository.findByTaskIdOrderByCreatedAtAsc("t1"))
            .thenReturn(List.of());
        when(this.requestQueue.submit(eq(ReattemptDecisionLlmExecutor.class), any()))
            .thenReturn(new ReattemptDecision(true, "worth another shot"));

        this.service.decideReattempt(ATTEMPT_ID);

        verify(this.taskGraphScheduler).dispatch(task, 2);
        verify(this.taskUpdateService).record(
            task, attempt, TaskUpdateType.UPDATE, "worth another shot",
            TaskUpdateAuthor.ORCHESTRATOR);
    }

    @Test
    void decideReattempt_whenTheLlmDeclines_doesNotDispatch() {
        var task = new TaskEntity();
        task.setId("t1");
        task.setTitle("title");
        task.setDescription("description");
        var attempt = attemptOf(task, 1);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        when(this.taskUpdateRepository.findFirstByTaskAttemptIdAndTypeInOrderByCreatedAtDesc(
                eq(ATTEMPT_ID), any()))
            .thenReturn(Optional.empty());
        when(this.taskUpdateRepository.findByTaskIdOrderByCreatedAtAsc("t1"))
            .thenReturn(List.of());
        when(this.requestQueue.submit(eq(ReattemptDecisionLlmExecutor.class), any()))
            .thenReturn(new ReattemptDecision(false, "not worth it"));

        this.service.decideReattempt(ATTEMPT_ID);

        verify(this.taskGraphScheduler, never()).dispatch(any(), any(Integer.class));
        verify(this.taskUpdateService).record(
            task, attempt, TaskUpdateType.UPDATE, "not worth it", TaskUpdateAuthor.ORCHESTRATOR);
    }

    @Test
    void decideReattempt_includesTheLatestFailureReasoningInTheRequest() {
        var task = new TaskEntity();
        task.setId("t1");
        task.setTitle("title");
        task.setDescription("description");
        var attempt = attemptOf(task, 1);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        var failureUpdate = new TaskUpdateEntity();
        failureUpdate.setType(TaskUpdateType.FAILED);
        failureUpdate.setDescription("did not converge");
        when(this.taskUpdateRepository.findFirstByTaskAttemptIdAndTypeInOrderByCreatedAtDesc(
                eq(ATTEMPT_ID), any()))
            .thenReturn(Optional.of(failureUpdate));
        when(this.taskUpdateRepository.findByTaskIdOrderByCreatedAtAsc("t1"))
            .thenReturn(List.of());
        when(this.requestQueue.submit(eq(ReattemptDecisionLlmExecutor.class), any()))
            .thenReturn(new ReattemptDecision(false, "not worth it"));

        this.service.decideReattempt(ATTEMPT_ID);

        var requestCaptor = ArgumentCaptor.forClass(ReattemptDecisionRequest.class);
        verify(this.requestQueue).submit(
            eq(ReattemptDecisionLlmExecutor.class), requestCaptor.capture());
        assertThat(requestCaptor.getValue().latestFailureReasoning()).isEqualTo(
            "did not converge");
        assertThat(requestCaptor.getValue().attemptNumber()).isEqualTo(1);
        assertThat(requestCaptor.getValue().maxAttempts()).isEqualTo(3);
    }

    @Test
    void decideReattempt_passesOnlyEarlierAttemptsUpdatesAsPriorUpdates() {
        var task = new TaskEntity();
        task.setId("t1");
        task.setTitle("title");
        task.setDescription("description");
        var earlierAttempt = attemptOf(task, 1);
        earlierAttempt.setId("earlier-attempt");
        var attempt = attemptOf(task, 2);
        when(this.taskAttemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(attempt));
        when(this.taskUpdateRepository.findFirstByTaskAttemptIdAndTypeInOrderByCreatedAtDesc(
                eq(ATTEMPT_ID), any()))
            .thenReturn(Optional.empty());
        when(this.taskUpdateRepository.findByTaskIdOrderByCreatedAtAsc("t1"))
            .thenReturn(List.of(
                updateOf(null, TaskUpdateType.CREATED, "task created"),
                updateOf(earlierAttempt, TaskUpdateType.FAILED, "earlier failure"),
                updateOf(attempt, TaskUpdateType.FAILED, "current failure")));
        when(this.requestQueue.submit(eq(ReattemptDecisionLlmExecutor.class), any()))
            .thenReturn(new ReattemptDecision(false, "not worth it"));

        this.service.decideReattempt(ATTEMPT_ID);

        var requestCaptor = ArgumentCaptor.forClass(ReattemptDecisionRequest.class);
        verify(this.requestQueue).submit(
            eq(ReattemptDecisionLlmExecutor.class), requestCaptor.capture());
        assertThat(requestCaptor.getValue().priorUpdateDescriptions())
            .containsExactly("Attempt 1 FAILED: earlier failure");
    }

    private static TaskUpdateEntity updateOf(
            final TaskAttemptEntity attempt, final TaskUpdateType type, final String description) {
        var update = new TaskUpdateEntity();
        update.setTaskAttempt(attempt);
        update.setType(type);
        update.setDescription(description);
        return update;
    }
}
