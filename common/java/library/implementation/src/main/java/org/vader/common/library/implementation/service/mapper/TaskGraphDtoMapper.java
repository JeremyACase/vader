package org.vader.common.library.implementation.service.mapper;

import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.model.vader.dto.TaskGraph;
import org.vader.common.model.vader.entity.TaskGraphEntity;

/** Maps {@link TaskGraphEntity} to {@link TaskGraph} DTOs. */
@Service
@Transactional
public class TaskGraphDtoMapper extends GenericDtoMapper<TaskGraphEntity, TaskGraph> {

    @Autowired
    private TaskDtoMapper taskDtoMapper;

    @Override
    public TaskGraph map(final TaskGraphEntity from) {
        TaskGraph to = null;
        if (Objects.nonNull(from)) {
            to = new TaskGraph();
            super.setAbstractModelFields(from, to);
            to.setTasks(this.taskDtoMapper.map(from.getTasks()));
        }
        return to;
    }
}
