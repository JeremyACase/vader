package org.vader.core.server.review;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.common.model.vader.entity.TaskUpdateAuthor;
import org.vader.common.model.vader.entity.TaskUpdateType;
import org.vader.core.server.review.model.RemainingSubtask;
import org.vader.core.server.workflow.TaskAttemptRepository;
import org.vader.core.server.workflow.TaskRepository;
import org.vader.core.server.workflow.TaskUpdateService;

/**
 * Carries one attempt's unfinished work to completion through runtime subtasks: a saga of three
 * steps, each recorded as a verdict against the attempt that was decomposed.
 *
 * <ol>
 *   <li><b>Decompose</b> -- the evaluator judged the attempt real but unfinished progress. Its
 *       remaining steps become subtasks of the attempt's task, chained to run one after another,
 *       and the attempt is verdicted {@link TaskUpdateType#DECOMPOSED}.</li>
 *   <li><b>Roll up</b> -- every subtask succeeded. Their results, concatenated in order, become
 *       the attempt's {@code rollupResult}, and it is verdicted {@link TaskUpdateType#COMPLETED}.
 *       </li>
 *   <li><b>Compensate</b> -- a subtask permanently failed. The attempt is verdicted
 *       {@link TaskUpdateType#FAILED}, blocking the task's dependents exactly as if it had failed
 *       on its own.</li>
 * </ol>
 *
 * <p>The roll-up is deterministic rather than an LLM summary: it runs inside the scheduler's
 * transaction, which must never block on an LLM call, and workflow synthesis already summarizes
 * every result at the end. The decomposed attempt's own partial result is deliberately left out
 * of it -- that unfinished text is what decomposition exists to keep away from downstream tasks.
 * </p>
 */
@Service
public class TaskDecompositionSaga {

    private static final Logger logger = LoggerFactory.getLogger(TaskDecompositionSaga.class);

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TaskAttemptRepository taskAttemptRepository;

    @Autowired
    private TaskUpdateService taskUpdateService;

    @Value("${vader.task-decomposition.max-depth:1}")
    private int maxDepth;

    @Value("${vader.task-decomposition.max-subtasks:3}")
    private int maxSubtasks;

    /**
     * Whether a task may be decomposed at all: the task is shallower than the depth cap (with the
     * default cap of 1, only plan-level tasks decompose; a cap of 0 turns decomposition off).
     *
     * @param task the task whose attempt the evaluator wants to decompose
     * @return {@code true} if it may be decomposed
     */
    public boolean canDecompose(final TaskEntity task) {
        return this.maxSubtasks > 0 && task.depth() < this.maxDepth;
    }

    /**
     * Step 1: splits an attempt's remaining work into chained subtasks of its task and verdicts
     * the attempt {@link TaskUpdateType#DECOMPOSED}. Steps beyond the subtask cap are dropped.
     *
     * @param attempt the attempt the evaluator judged unfinished
     * @param remainingSubtasks the evaluator's remaining steps, in order; must not be empty
     * @param reasoning the evaluator's reasoning, recorded on the verdict
     */
    @Transactional
    public void decompose(
            final TaskAttemptEntity attempt, final List<RemainingSubtask> remainingSubtasks,
            final String reasoning) {
        var task = attempt.getTask();
        var subtasks = new ArrayList<TaskEntity>();
        remainingSubtasks.stream().limit(this.maxSubtasks)
            .forEach(step -> subtasks.add(this.saveChained(attempt, step, subtasks)));
        task.getSubTasks().addAll(subtasks);

        subtasks.forEach(subtask -> this.taskUpdateService.record(
            subtask, null, TaskUpdateType.CREATED, subtask.getDescription(),
            TaskUpdateAuthor.EVALUATOR));
        this.taskUpdateService.record(task, attempt, TaskUpdateType.DECOMPOSED,
            reasoning + "\n\nRemaining work was split into subtasks:\n" + titlesOf(subtasks),
            TaskUpdateAuthor.EVALUATOR);
        logger.info("Decomposed attempt {} of task {} into {} subtask(s)",
            attempt.getId(), task.getId(), subtasks.size());
    }

    /**
     * The subtasks one decomposed attempt spawned, in the order they run.
     *
     * @param attempt the decomposed attempt
     * @return its subtasks, first step first
     */
    public List<TaskEntity> subtasksOf(final TaskAttemptEntity attempt) {
        var subtasks = attempt.getTask().getSubTasks().stream()
            .filter(subtask -> attempt.equals(subtask.getSpawnedByAttempt()))
            .toList();
        return subtasks.stream()
            .sorted(Comparator.comparingInt(subtask -> chainPosition(subtask, subtasks)))
            .toList();
    }

    /**
     * Step 2: every subtask succeeded -- rolls their results up onto the decomposed attempt and
     * verdicts it {@link TaskUpdateType#COMPLETED}.
     *
     * @param attempt the decomposed attempt
     */
    @Transactional
    public void rollUp(final TaskAttemptEntity attempt) {
        var subtasks = this.subtasksOf(attempt);
        attempt.setRollupResult(subtasks.stream()
            .map(subtask -> "## " + subtask.getTitle() + "\n" + this.resultOf(subtask))
            .collect(Collectors.joining("\n\n")));
        this.taskAttemptRepository.save(attempt);
        this.taskUpdateService.record(attempt.getTask(), attempt, TaskUpdateType.COMPLETED,
            "All " + subtasks.size() + " subtask(s) completed; their results were rolled up "
                + "into this task's result.",
            TaskUpdateAuthor.SYSTEM);
        logger.info("Rolled up {} subtask(s) onto attempt {}", subtasks.size(), attempt.getId());
    }

    /**
     * Step 3: a subtask permanently failed -- verdicts the decomposed attempt
     * {@link TaskUpdateType#FAILED}.
     *
     * @param attempt the decomposed attempt
     * @param failedSubtask the subtask that failed
     */
    @Transactional
    public void compensate(final TaskAttemptEntity attempt, final TaskEntity failedSubtask) {
        this.taskUpdateService.record(attempt.getTask(), attempt, TaskUpdateType.FAILED,
            "Subtask \"" + failedSubtask.getTitle() + "\" failed permanently, so this task's "
                + "remaining work could not be completed.",
            TaskUpdateAuthor.SYSTEM);
        logger.info("Subtask {} failed; failing decomposed attempt {}",
            failedSubtask.getId(), attempt.getId());
    }

    /**
     * Persists one step as a subtask depending on the step saved just before it, if any. Returns
     * what {@code save} returns, never the instance built here: ids are assigned up front, so
     * Spring Data merges rather than persists, and only the returned copy is managed -- the one
     * that may be linked from other entities and added to the parent's cascading collection.
     */
    private TaskEntity saveChained(
            final TaskAttemptEntity attempt, final RemainingSubtask step,
            final List<TaskEntity> savedSoFar) {
        var subtask = new TaskEntity();
        subtask.setTitle(step.title());
        subtask.setDescription(step.description());
        subtask.setParentTask(attempt.getTask());
        subtask.setSpawnedByAttempt(attempt);
        if (!savedSoFar.isEmpty()) {
            subtask.getDependsOn().add(savedSoFar.getLast());
        }
        return this.taskRepository.save(subtask);
    }

    /**
     * A subtask's position in its chain: how many earlier siblings precede it. Each subtask
     * depends on at most one sibling (the one before it), so this follows that single edge back.
     */
    private static int chainPosition(final TaskEntity subtask, final List<TaskEntity> siblings) {
        return subtask.getDependsOn().stream()
            .filter(siblings::contains)
            .findFirst()
            .map(previous -> chainPosition(previous, siblings) + 1)
            .orElse(0);
    }

    /** A subtask's own result -- or its rolled-up result, if it was itself decomposed. */
    private String resultOf(final TaskEntity subtask) {
        return this.taskAttemptRepository.findFirstByTaskIdOrderByAttemptNumberDesc(subtask.getId())
            .map(TaskAttemptEntity::effectiveResult)
            .orElse("(no result reported)");
    }

    private static String titlesOf(final List<TaskEntity> subtasks) {
        return IntStream.range(0, subtasks.size())
            .mapToObj(index -> (index + 1) + ". " + subtasks.get(index).getTitle())
            .collect(Collectors.joining("\n"));
    }
}
