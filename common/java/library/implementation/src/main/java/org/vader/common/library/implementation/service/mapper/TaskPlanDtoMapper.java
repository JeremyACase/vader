package org.vader.common.library.implementation.service.mapper;

import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.model.vader.dto.TaskPlan;
import org.vader.common.model.vader.entity.TaskPlanEntity;

/** Maps {@link TaskPlanEntity} to {@link TaskPlan} DTOs. */
@Service
@Transactional
public class TaskPlanDtoMapper extends GenericDtoMapper<TaskPlanEntity, TaskPlan> {

    @Autowired
    private TaskGraphDtoMapper taskGraphDtoMapper;

    @Override
    public TaskPlan map(final TaskPlanEntity from) {
        TaskPlan to = null;
        if (Objects.nonNull(from)) {
            to = new TaskPlan();
            super.setAbstractModelFields(from, to);
            to.setObjective(from.getObjective());
            to.setTaskGraph(this.taskGraphDtoMapper.map(from.getTaskGraph()));
        }
        return to;
    }
}
