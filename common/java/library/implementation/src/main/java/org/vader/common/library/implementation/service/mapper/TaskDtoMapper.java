package org.vader.common.library.implementation.service.mapper;

import java.util.ArrayList;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vader.common.model.vader.dto.Task;
import org.vader.common.model.vader.entity.TaskEntity;

/**
 * Maps {@link TaskEntity} to {@link Task} DTOs.
 *
 * <p>{@code subTasks} are mapped recursively. The parent task and the {@code dependsOn} tasks are
 * emitted as shallow id references -- embedding them would recurse back through this task's own
 * subtree.</p>
 */
@Service
@Transactional
public class TaskDtoMapper extends GenericDtoMapper<TaskEntity, Task> {

    @Override
    public Task map(final TaskEntity from) {
        Task to = null;
        if (Objects.nonNull(from)) {
            to = new Task();
            super.setAbstractModelFields(from, to);
            to.setTitle(from.getTitle());
            to.setDescription(from.getDescription());

            if (Objects.nonNull(from.getParentTask())) {
                to.setParentTaskId(from.getParentTask().getId());
            }

            to.setSubTasks(this.map(from.getSubTasks()));

            var dependsOnTaskIds = new ArrayList<String>();
            if (Objects.nonNull(from.getDependsOn())) {
                for (var dependency : from.getDependsOn()) {
                    dependsOnTaskIds.add(dependency.getId());
                }
            }
            to.setDependsOnTaskIds(dependsOnTaskIds);
        }
        return to;
    }
}
