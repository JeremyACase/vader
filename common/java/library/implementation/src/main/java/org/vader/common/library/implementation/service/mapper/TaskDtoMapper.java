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
 * subtree. {@code taskUpdates} are emitted as shallow id references too, per the default mapper
 * convention for the "many" side of a relationship.</p>
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

            var taskUpdateIds = new ArrayList<String>();
            if (Objects.nonNull(from.getTaskUpdates())) {
                for (var taskUpdate : from.getTaskUpdates()) {
                    taskUpdateIds.add(taskUpdate.getId());
                }
            }
            to.setTaskUpdateIds(taskUpdateIds);
        }
        return to;
    }
}
