package org.vader.common.library.implementation.service.mapper;

import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.model.vader.dto.TaskAttemptToolCall;
import org.vader.common.model.vader.entity.TaskAttemptToolCallEntity;

/**
 * Maps {@link TaskAttemptToolCallEntity} to {@link TaskAttemptToolCall} DTOs. The owning attempt
 * is emitted as a shallow id reference, same convention as every other cross-model reference in
 * this package.
 */
@Service
@Transactional
public class TaskAttemptToolCallDtoMapper
    extends GenericDtoMapper<TaskAttemptToolCallEntity, TaskAttemptToolCall> {

    @Override
    public TaskAttemptToolCall map(final TaskAttemptToolCallEntity from) {
        TaskAttemptToolCall to = null;
        if (Objects.nonNull(from)) {
            to = new TaskAttemptToolCall();
            super.setAbstractModelFields(from, to);
            if (Objects.nonNull(from.getTaskAttempt())) {
                to.setTaskAttemptId(from.getTaskAttempt().getId());
            }
            to.setToolCallId(from.getToolCallId());
            to.setToolName(from.getToolName());
            to.setArgumentsJson(from.getArgumentsJson());
            to.setResultJson(from.getResultJson());
        }
        return to;
    }
}
