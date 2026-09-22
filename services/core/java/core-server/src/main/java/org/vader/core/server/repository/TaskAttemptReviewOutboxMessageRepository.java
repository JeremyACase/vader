package org.vader.core.server.repository;

import java.util.List;
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
}
