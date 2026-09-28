package org.vader.core.server.repository;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.vader.common.model.vader.entity.OutboxMessageStatus;
import org.vader.common.model.vader.entity.TaskAttemptReviewOutboxMessageEntity;

/** Spring Data repository for {@link TaskAttemptReviewOutboxMessageEntity} queue messages. */
public interface TaskAttemptReviewOutboxMessageRepository
    extends OutboxMessageRepository<TaskAttemptReviewOutboxMessageEntity> {

    /**
     * Whether a still-open (not yet processed or failed) review message already exists for the
     * given attempt -- {@code TaskGraphScheduler} checks this before enqueuing another one, since
     * it may re-scan the same under-review task on every settlement event until review completes.
     *
     * @param taskAttemptId the attempt id
     * @param openStatuses the non-terminal statuses to consider (pending, claimed)
     * @return {@code true} if an open review message already exists for this attempt
     */
    boolean existsByTaskAttemptIdAndStatusIn(
        String taskAttemptId, List<OutboxMessageStatus> openStatuses);

    /**
     * The oldest review message in {@code status} that is due -- never deferred, or deferred
     * until a time that has now passed. Used in place of the generic oldest-pending lookup so a
     * review deferred during an LLM outage is not reclaimed before its retry time.
     *
     * @param status the status to match (pending)
     * @param now the current time
     * @param limit how many to return; pass {@code PageRequest.of(0, 1)} for just the oldest
     * @return the due messages, oldest first
     */
    @Query("select m from TaskAttemptReviewOutboxMessageEntity m where m.status = :status "
        + "and (m.nextAttemptAt is null or m.nextAttemptAt <= :now) order by m.createdAt asc")
    List<TaskAttemptReviewOutboxMessageEntity> findDue(
        @Param("status") OutboxMessageStatus status,
        @Param("now") OffsetDateTime now,
        Pageable limit);

    /**
     * Whether any review in the given workflow is still deferred, waiting out an LLM outage --
     * i.e. whether the workflow should still read {@code AWAITING_LLM}.
     *
     * @param workflowId the workflow id
     * @param status the status a deferred review sits in (pending)
     * @return {@code true} if at least one review in the workflow is deferred
     */
    @Query("select count(m) > 0 from TaskAttemptReviewOutboxMessageEntity m "
        + "where m.status = :status and m.nextAttemptAt is not null "
        + "and m.taskAttempt.task.taskGraph.taskPlan.workflow.id = :workflowId")
    boolean existsDeferredInWorkflow(
        @Param("workflowId") String workflowId,
        @Param("status") OutboxMessageStatus status);
}
