package org.vader.common.library.implementation.service.mapper;

import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.model.vader.dto.TaskAttemptTranscript;
import org.vader.common.model.vader.entity.TaskAttemptTranscriptEntity;

/**
 * Maps {@link TaskAttemptTranscriptEntity} to {@link TaskAttemptTranscript} DTOs. The owning
 * attempt is emitted as a shallow id reference, same convention as every other cross-model
 * reference in this package.
 */
@Service
@Transactional
public class TaskAttemptTranscriptDtoMapper
    extends GenericDtoMapper<TaskAttemptTranscriptEntity, TaskAttemptTranscript> {

    @Override
    public TaskAttemptTranscript map(final TaskAttemptTranscriptEntity from) {
        TaskAttemptTranscript to = null;
        if (Objects.nonNull(from)) {
            to = new TaskAttemptTranscript();
            super.setAbstractModelFields(from, to);
            if (Objects.nonNull(from.getTaskAttempt())) {
                to.setTaskAttemptId(from.getTaskAttempt().getId());
            }
            to.setTurnIndex(from.getTurnIndex());
            to.setPrompt(from.getPrompt());
            to.setResponse(from.getResponse());
            to.setTokensSpent(from.getTokensSpent());
        }
        return to;
    }
}
