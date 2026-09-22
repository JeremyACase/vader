package org.vader.common.library.implementation.service.mapper;

import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.model.vader.dto.TaskUpdate;
import org.vader.common.model.vader.entity.TaskUpdateEntity;

/**
 * Maps {@link TaskUpdateEntity} to {@link TaskUpdate} DTOs. The owning task is emitted as a
 * shallow id reference, same convention as every other cross-model reference in this package.
 */
@Service
@Transactional
public class TaskUpdateDtoMapper extends GenericDtoMapper<TaskUpdateEntity, TaskUpdate> {

    @Override
    public TaskUpdate map(final TaskUpdateEntity from) {
        TaskUpdate to = null;
        if (Objects.nonNull(from)) {
            to = new TaskUpdate();
            super.setAbstractModelFields(from, to);
            if (Objects.nonNull(from.getTask())) {
                to.setTaskId(from.getTask().getId());
            }
            if (Objects.nonNull(from.getTaskAttempt())) {
                to.setTaskAttemptId(from.getTaskAttempt().getId());
            }
            to.setType(from.getType());
            to.setAuthor(from.getAuthor());
            to.setDescription(from.getDescription());
        }
        return to;
    }
}
