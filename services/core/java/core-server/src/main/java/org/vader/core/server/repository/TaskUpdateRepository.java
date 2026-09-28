package org.vader.core.server.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.vader.common.model.vader.entity.TaskUpdateEntity;
import org.vader.common.model.vader.entity.TaskUpdateType;

/** Spring Data repository for {@link TaskUpdateEntity}. */
public interface TaskUpdateRepository extends JpaRepository<TaskUpdateEntity, String> {

    /**
     * Finds the most recent update of one of the given types recorded against a specific attempt
     * -- backs {@code TaskGraphScheduler}'s derivation of whether a terminal attempt has been
     * verdicted yet, and if so, as what.
     *
     * @param taskAttemptId the attempt id
     * @param types the update types to consider (a verdict is always exactly one of
     *     {@code COMPLETED}, {@code FAILED}, {@code TIMED_OUT})
     * @return the matching update, most recent first, or empty if the attempt has no verdict yet
     */
    Optional<TaskUpdateEntity> findFirstByTaskAttemptIdAndTypeInOrderByCreatedAtDesc(
        String taskAttemptId, List<TaskUpdateType> types);

    /**
     * Finds every update recorded against a task, oldest first -- the history an evaluator or
     * orchestrator reads back for context before forming its own judgment.
     *
     * @param taskId the task id
     * @return the task's updates, oldest first
     */
    List<TaskUpdateEntity> findByTaskIdOrderByCreatedAtAsc(String taskId);
}
