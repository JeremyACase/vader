package org.vader.core.server.workflow;

import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskEntity;

/**
 * Filters a task's subtasks down to those still part of its work. A runtime subtask belongs to the
 * attempt that spawned it: once the task is attempted again, the earlier attempt's subtasks are
 * superseded -- kept as history, but no longer scheduled, counted toward the workflow's outcome,
 * or shown to agents as part of the plan. Subtasks the planner itself nested are always live.
 */
@Component
public class LiveSubtaskFilter {

    @Autowired
    private TaskAttemptRepository taskAttemptRepository;

    /**
     * The subtasks of a task that are still part of its work.
     *
     * @param task the parent task
     * @return its planner-nested subtasks plus those spawned by its latest attempt
     */
    public List<TaskEntity> liveSubtasksOf(final TaskEntity task) {
        var latest = this.taskAttemptRepository
            .findFirstByTaskIdOrderByAttemptNumberDesc(task.getId())
            .orElse(null);
        return task.getSubTasks().stream()
            .filter(subtask -> isLive(subtask, latest))
            .toList();
    }

    private static boolean isLive(final TaskEntity subtask, final TaskAttemptEntity latest) {
        var spawnedBy = subtask.getSpawnedByAttempt();
        return Objects.isNull(spawnedBy) || spawnedBy.equals(latest);
    }
}
