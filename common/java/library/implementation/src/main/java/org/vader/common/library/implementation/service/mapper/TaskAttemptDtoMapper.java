package org.vader.common.library.implementation.service.mapper;

import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.model.vader.dto.TaskAttempt;
import org.vader.common.model.vader.entity.TaskAttemptEntity;

/**
 * Maps {@link TaskAttemptEntity} to {@link TaskAttempt} DTOs. The owning task is emitted as a
 * shallow id reference, same convention as every other cross-model reference in this package.
 */
@Service
@Transactional
public class TaskAttemptDtoMapper extends GenericDtoMapper<TaskAttemptEntity, TaskAttempt> {

    @Override
    public TaskAttempt map(final TaskAttemptEntity from) {
        TaskAttempt to = null;
        if (Objects.nonNull(from)) {
            to = new TaskAttempt();
            super.setAbstractModelFields(from, to);
            if (Objects.nonNull(from.getTask())) {
                to.setTaskId(from.getTask().getId());
            }
            to.setAttemptNumber(from.getAttemptNumber());
            to.setStatus(from.getStatus());
            to.setDispatchedAt(from.getDispatchedAt());
            to.setStartedAt(from.getStartedAt());
            to.setLastHeartbeatAt(from.getLastHeartbeatAt());
            to.setCompletedAt(from.getCompletedAt());
            to.setTurnsUsed(from.getTurnsUsed());
            to.setTokensUsed(from.getTokensUsed());
            to.setResult(from.getResult());
            to.setFailureReason(from.getFailureReason());
        }
        return to;
    }
}
