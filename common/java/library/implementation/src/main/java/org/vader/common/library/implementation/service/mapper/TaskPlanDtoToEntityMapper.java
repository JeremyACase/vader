package org.vader.common.library.implementation.service.mapper;

import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.vader.common.library.implementation.interfaces.mapper.InterfaceDtoToEntityMapper;
import org.vader.common.model.vader.dto.TaskPlan;
import org.vader.common.model.vader.entity.TaskPlanEntity;

/**
 * Maps a {@link TaskPlan} DTO to a transient {@link TaskPlanEntity}, wiring the owning side of the
 * plan-to-graph association so a single {@code save} cascades the whole decomposition.
 */
@Service
public class TaskPlanDtoToEntityMapper
    implements InterfaceDtoToEntityMapper<TaskPlan, TaskPlanEntity> {

    @Autowired
    private TaskGraphDtoToEntityMapper taskGraphDtoToEntityMapper;

    @Override
    public TaskPlanEntity map(final TaskPlan from) {
        if (Objects.isNull(from)) {
            return null;
        }

        var to = new TaskPlanEntity();
        to.setObjective(from.getObjective());

        var taskGraph = this.taskGraphDtoToEntityMapper.map(from.getTaskGraph());
        if (Objects.nonNull(taskGraph)) {
            to.setTaskGraph(taskGraph);
            taskGraph.setTaskPlan(to);
        }
        return to;
    }
}
