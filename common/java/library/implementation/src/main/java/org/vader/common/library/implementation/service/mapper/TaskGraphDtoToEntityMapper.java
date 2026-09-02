package org.vader.common.library.implementation.service.mapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.vader.common.library.implementation.interfaces.mapper.InterfaceDtoToEntityMapper;
import org.vader.common.model.vader.dto.Task;
import org.vader.common.model.vader.dto.TaskGraph;
import org.vader.common.model.vader.entity.TaskEntity;
import org.vader.common.model.vader.entity.TaskGraphEntity;

/**
 * Maps a {@link TaskGraph} DTO to a transient {@link TaskGraphEntity}.
 *
 * <p>Only root tasks (those with no parent) are attached directly to the graph; the rest hang off
 * their parent via {@code subTasks}, matching how {@code TaskGraphDtoMapper} reads them back out.
 * {@code dependsOnTaskIds} are resolved against the DTO-supplied {@code id}s of the tasks in the
 * same graph -- these ids are used only to wire the in-memory edges and are never persisted as
 * entity identities.</p>
 */
@Service
public class TaskGraphDtoToEntityMapper
    implements InterfaceDtoToEntityMapper<TaskGraph, TaskGraphEntity> {

    @Override
    public TaskGraphEntity map(final TaskGraph from) {
        if (Objects.isNull(from)) {
            return null;
        }

        var to = new TaskGraphEntity();
        var tasksById = new HashMap<String, TaskEntity>();

        for (var rootDto : from.getTasks()) {
            var rootEntity = this.mapTask(rootDto, to, null, tasksById);
            to.getTasks().add(rootEntity);
        }

        this.resolveDependencies(from.getTasks(), tasksById);
        return to;
    }

    private TaskEntity mapTask(
        final Task from,
        final TaskGraphEntity graph,
        final TaskEntity parent,
        final Map<String, TaskEntity> tasksById) {

        var to = new TaskEntity();
        to.setTitle(from.getTitle());
        to.setDescription(from.getDescription());
        to.setParentTask(parent);
        if (Objects.isNull(parent)) {
            to.setTaskGraph(graph);
        }

        if (Objects.nonNull(from.getId())) {
            tasksById.put(from.getId(), to);
        }

        for (var subTaskDto : from.getSubTasks()) {
            to.getSubTasks().add(this.mapTask(subTaskDto, graph, to, tasksById));
        }
        return to;
    }

    private void resolveDependencies(
        final List<Task> froms,
        final Map<String, TaskEntity> tasksById) {

        for (var from : froms) {
            if (Objects.nonNull(from.getId()) && !from.getDependsOnTaskIds().isEmpty()) {
                var dependent = tasksById.get(from.getId());
                for (var dependencyId : from.getDependsOnTaskIds()) {
                    var dependency = tasksById.get(dependencyId);
                    if (Objects.isNull(dependency)) {
                        throw new IllegalArgumentException(
                            "Task '" + from.getId() + "' depends on unknown task id '"
                                + dependencyId + "'");
                    }
                    dependent.getDependsOn().add(dependency);
                }
            }
            this.resolveDependencies(from.getSubTasks(), tasksById);
        }
    }
}
