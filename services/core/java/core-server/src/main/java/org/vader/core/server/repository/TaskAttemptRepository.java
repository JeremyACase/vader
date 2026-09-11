package org.vader.core.server.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.vader.common.model.vader.entity.TaskAttemptEntity;
import org.vader.common.model.vader.entity.TaskAttemptStatus;

/** Spring Data repository for {@link TaskAttemptEntity}. */
public interface TaskAttemptRepository extends JpaRepository<TaskAttemptEntity, String> {

    /**
     * Finds every attempt made at the given task, most recent first.
     *
     * @param taskId the task id
     * @return the task's attempts, most recent first
     */
    List<TaskAttemptEntity> findByTaskIdOrderByAttemptNumberDesc(String taskId);

    /**
     * Finds the most recent attempt made at the given task, if any.
     *
     * @param taskId the task id
     * @return the latest attempt, or empty if the task has never been attempted
     */
    Optional<TaskAttemptEntity> findFirstByTaskIdOrderByAttemptNumberDesc(String taskId);

    /**
     * Finds every open (non-terminal) attempt whose most recent sign of life -- its last
     * heartbeat, or failing that when it was dispatched, or failing that when it was created --
     * is older than {@code staleBefore}. Backs {@code TaskAttemptReaper}: these are attempts a
     * harness is never going to report back on.
     *
     * @param openStatuses the non-terminal statuses to consider
     * @param staleBefore the cutoff; attempts with no sign of life since before this are stale
     * @return the stale attempts
     */
    @Query("SELECT a FROM TaskAttemptEntity a WHERE a.status IN :openStatuses "
        + "AND COALESCE(a.lastHeartbeatAt, a.dispatchedAt, a.createdAt) < :staleBefore")
    List<TaskAttemptEntity> findStale(
        @Param("openStatuses") List<TaskAttemptStatus> openStatuses,
        @Param("staleBefore") OffsetDateTime staleBefore);
}
