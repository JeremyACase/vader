package org.vader.core.server.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskAttemptStatus;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.common.model.vader.entity.TaskUpdateAuthor;
import org.vader.common.model.vader.entity.TaskUpdateType;
import org.vader.core.server.review.model.RemainingSubtask;
import org.vader.core.server.workflow.TaskAttemptRepository;
import org.vader.core.server.workflow.TaskRepository;
import org.vader.core.server.workflow.TaskUpdateService;

class TaskDecompositionSagaTest {

    private TaskRepository taskRepository;
    private TaskAttemptRepository taskAttemptRepository;
    private TaskUpdateService taskUpdateService;
    private TaskDecompositionSaga saga;

    @BeforeEach
    void setUp() {
        this.taskRepository = mock(TaskRepository.class);
        this.taskAttemptRepository = mock(TaskAttemptRepository.class);
        this.taskUpdateService = mock(TaskUpdateService.class);

        this.saga = new TaskDecompositionSaga();
        ReflectionTestUtils.setField(this.saga, "taskRepository", this.taskRepository);
        ReflectionTestUtils.setField(
            this.saga, "taskAttemptRepository", this.taskAttemptRepository);
        ReflectionTestUtils.setField(this.saga, "taskUpdateService", this.taskUpdateService);
        ReflectionTestUtils.setField(this.saga, "enabled", true);
        ReflectionTestUtils.setField(this.saga, "maxDepth", 1);
        ReflectionTestUtils.setField(this.saga, "maxSubtasks", 3);
        when(this.taskRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static TaskEntity task(final String title) {
        var task = new TaskEntity();
        task.setTitle(title);
        task.setDescription("Do " + title + ".");
        return task;
    }

    private static TaskAttemptEntity attemptOf(final TaskEntity task) {
        var attempt = new TaskAttemptEntity();
        attempt.setTask(task);
        attempt.setAttemptNumber(1);
        attempt.setStatus(TaskAttemptStatus.SUCCEEDED);
        attempt.setResult("Here's the corrected approach. Let's proceed.");
        return attempt;
    }

    private static List<RemainingSubtask> steps(final int count) {
        return IntStream.range(0, count)
            .mapToObj(index -> new RemainingSubtask(
                "Step " + UUID.randomUUID(), "Carry out part " + index + " of the work."))
            .toList();
    }

    private void latestResultFor(final TaskEntity task, final String result) {
        var attempt = attemptOf(task);
        attempt.setResult(result);
        when(this.taskAttemptRepository.findFirstByTaskIdOrderByAttemptNumberDesc(task.getId()))
            .thenReturn(Optional.of(attempt));
    }

    @Test
    void canDecompose_allowsRootTasksOnlyAtTheDefaultDepth() {
        var root = task("analyze");
        var subtask = task("step");
        subtask.setParentTask(root);

        assertThat(this.saga.canDecompose(root)).isTrue();
        assertThat(this.saga.canDecompose(subtask)).isFalse();
    }

    @Test
    void canDecompose_isFalseWhenDisabled() {
        ReflectionTestUtils.setField(this.saga, "enabled", false);

        assertThat(this.saga.canDecompose(task("analyze"))).isFalse();
    }

    @Test
    void decompose_createsSubtasksChainedInOrderAndRecordsTheVerdict() {
        var parent = task("clean");
        var attempt = attemptOf(parent);
        var steps = steps(3);

        this.saga.decompose(attempt, steps, "code never ran");

        var subtasks = this.saga.subtasksOf(attempt);
        assertThat(subtasks).extracting(TaskEntity::getTitle)
            .containsExactlyElementsOf(steps.stream().map(RemainingSubtask::title).toList());
        assertThat(subtasks).allSatisfy(subtask -> {
            assertThat(subtask.getParentTask()).isSameAs(parent);
            assertThat(subtask.getSpawnedByAttempt()).isSameAs(attempt);
            assertThat(subtask.getTaskGraph()).isNull();
        });
        assertThat(subtasks.get(0).getDependsOn()).isEmpty();
        assertThat(subtasks.get(1).getDependsOn()).containsExactly(subtasks.get(0));
        assertThat(subtasks.get(2).getDependsOn()).containsExactly(subtasks.get(1));
        verify(this.taskRepository, times(3)).save(any());
        verify(this.taskUpdateService, times(3)).record(
            any(), isNull(), eq(TaskUpdateType.CREATED), anyString(),
            eq(TaskUpdateAuthor.EVALUATOR));
        verify(this.taskUpdateService).record(
            same(parent), same(attempt), eq(TaskUpdateType.DECOMPOSED),
            contains("code never ran"), eq(TaskUpdateAuthor.EVALUATOR));
    }

    @Test
    void decompose_dropsStepsBeyondTheSubtaskCap() {
        var parent = task("clean");
        var attempt = attemptOf(parent);

        this.saga.decompose(attempt, steps(5), "too much left");

        assertThat(this.saga.subtasksOf(attempt)).hasSize(3);
    }

    @Test
    void subtasksOf_ignoresSubtasksSpawnedByAnotherAttempt() {
        var parent = task("clean");
        var first = attemptOf(parent);
        var second = attemptOf(parent);
        this.saga.decompose(first, steps(1), "first");
        this.saga.decompose(second, steps(2), "second");

        assertThat(this.saga.subtasksOf(first)).hasSize(1);
        assertThat(this.saga.subtasksOf(second)).hasSize(2);
    }

    @Test
    void rollUp_concatenatesSubtaskResultsInOrderAndLeavesOutThePartialResult() {
        var parent = task("clean");
        var attempt = attemptOf(parent);
        this.saga.decompose(attempt, steps(2), "unfinished");
        var subtasks = this.saga.subtasksOf(attempt);
        this.latestResultFor(subtasks.get(0), "Filled 12 missing values.");
        this.latestResultFor(subtasks.get(1), "No missing values remain.");

        this.saga.rollUp(attempt);

        assertThat(attempt.getRollupResult())
            .contains(subtasks.get(0).getTitle(), "Filled 12 missing values.")
            .doesNotContain("Let's proceed");
        assertThat(attempt.getRollupResult().indexOf("Filled 12"))
            .isLessThan(attempt.getRollupResult().indexOf("No missing values"));
        assertThat(attempt.effectiveResult()).isEqualTo(attempt.getRollupResult());
        verify(this.taskAttemptRepository).save(attempt);
        verify(this.taskUpdateService).record(
            same(parent), same(attempt), eq(TaskUpdateType.COMPLETED), anyString(),
            eq(TaskUpdateAuthor.SYSTEM));
    }

    @Test
    void compensate_failsTheDecomposedAttemptNamingTheFailedSubtask() {
        var parent = task("clean");
        var attempt = attemptOf(parent);
        var failed = task("verify");

        this.saga.compensate(attempt, failed);

        verify(this.taskUpdateService).record(
            same(parent), same(attempt), eq(TaskUpdateType.FAILED), contains("verify"),
            eq(TaskUpdateAuthor.SYSTEM));
    }
}
