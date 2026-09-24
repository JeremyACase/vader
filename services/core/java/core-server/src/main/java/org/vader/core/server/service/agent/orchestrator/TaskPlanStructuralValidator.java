package org.vader.core.server.service.agent.orchestrator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.vader.common.model.vader.dto.Task;
import org.vader.common.model.vader.dto.TaskPlan;

/**
 * Cheap, deterministic structural checks on a freshly-decomposed {@link TaskPlan}, run before any
 * LLM critique is even attempted: a structurally-broken plan (a dangling dependency reference, a
 * dependency cycle, no tasks at all) is already known to be wrong, so there is no reason to spend
 * an LLM call finding that out.
 */
public final class TaskPlanStructuralValidator {

    private TaskPlanStructuralValidator() {
    }

    /**
     * Validates a task plan's structure.
     *
     * @param taskPlan the plan to validate
     * @return the result: valid, or invalid with a human-readable (and LLM-actionable) problem
     */
    public static TaskPlanValidationResult validate(final TaskPlan taskPlan) {
        var tasks = flatten(taskPlan.getTaskGraph().getTasks());
        return tasks.isEmpty()
            ? new TaskPlanValidationResult(false, "The plan has no tasks at all.")
            : validateDependencies(tasks);
    }

    private static TaskPlanValidationResult validateDependencies(final List<Task> tasks) {
        var byId = new HashMap<String, Task>();
        tasks.forEach(task -> {
            if (task.getId() != null) {
                byId.put(task.getId(), task);
            }
        });

        var dangling = findDanglingDependency(tasks, byId);
        return dangling != null
            ? new TaskPlanValidationResult(false, dangling)
            : validateAcyclic(tasks, byId);
    }

    private static String findDanglingDependency(
            final List<Task> tasks, final Map<String, Task> byId) {
        return tasks.stream()
            .flatMap(task -> task.getDependsOnTaskIds().stream()
                .filter(dependencyId -> !byId.containsKey(dependencyId))
                .map(dependencyId -> "Task '" + task.getTitle()
                    + "' depends on an unknown task id '" + dependencyId + "'."))
            .findFirst()
            .orElse(null);
    }

    private static TaskPlanValidationResult validateAcyclic(
            final List<Task> tasks, final Map<String, Task> byId) {
        var visiting = new HashSet<String>();
        var visited = new HashSet<String>();
        var cycleAt = tasks.stream()
            .filter(task -> task.getId() != null && !visited.contains(task.getId()))
            .filter(task -> hasCycle(task.getId(), byId, visiting, visited))
            .map(Task::getTitle)
            .findFirst()
            .orElse(null);
        return cycleAt == null
            ? new TaskPlanValidationResult(true, null)
            : new TaskPlanValidationResult(
                false, "The plan has a dependency cycle involving task '" + cycleAt + "'.");
    }

    private static boolean hasCycle(
            final String taskId, final Map<String, Task> byId,
            final Set<String> visiting, final Set<String> visited) {
        boolean cyclic;
        if (visiting.contains(taskId)) {
            cyclic = true;
        } else if (visited.contains(taskId)) {
            cyclic = false;
        } else {
            cyclic = dependenciesHaveCycle(taskId, byId, visiting, visited);
        }
        return cyclic;
    }

    private static boolean dependenciesHaveCycle(
            final String taskId, final Map<String, Task> byId,
            final Set<String> visiting, final Set<String> visited) {
        visiting.add(taskId);
        var cyclic = byId.get(taskId).getDependsOnTaskIds().stream()
            .filter(byId::containsKey)
            .anyMatch(dependencyId -> hasCycle(dependencyId, byId, visiting, visited));
        visiting.remove(taskId);
        visited.add(taskId);
        return cyclic;
    }

    private static List<Task> flatten(final List<Task> tasks) {
        var flat = new ArrayList<Task>();
        tasks.forEach(task -> {
            flat.add(task);
            flat.addAll(flatten(task.getSubTasks()));
        });
        return flat;
    }
}
