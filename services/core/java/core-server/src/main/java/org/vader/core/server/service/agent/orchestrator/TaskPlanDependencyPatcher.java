package org.vader.core.server.service.agent.orchestrator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.vader.common.model.vader.dto.Task;
import org.vader.common.model.vader.dto.TaskPlan;
import org.vader.core.server.models.TaskPlanRefinementVerdict.MissingDependency;

/**
 * Adds the dependencies a plan critique found missing directly to the plan, instead of sending the
 * whole plan back to be re-planned.
 *
 * <p>A small model reliably <em>spots</em> a missing dependency ("reading the spreadsheet has to
 * happen before identifying key data") but, asked to re-plan, regenerates the plan with the edge
 * still missing -- qwen2.5:3b did exactly that twice in a row. Applying the edge it named is
 * deterministic and cannot make the plan worse: an edge is added only if both titles name tasks
 * in the plan, it isn't a task depending on itself or an edge that already exists, and it would
 * not create a cycle. Anything else the critic proposed is skipped, leaving the plan as valid as
 * it was.</p>
 */
public final class TaskPlanDependencyPatcher {

    private TaskPlanDependencyPatcher() {
    }

    /**
     * Adds every valid proposed dependency to the plan's tasks, in place.
     *
     * @param taskPlan the plan to patch
     * @param missingDependencies the dependencies the critique found missing, by task title
     * @return a description of each dependency actually added, for logging; empty if none were
     */
    public static List<String> apply(
            final TaskPlan taskPlan, final List<MissingDependency> missingDependencies) {
        var tasks = taskPlan.getTaskGraph().getTasks();
        return missingDependencies.stream()
            .flatMap(missing -> Objects.requireNonNullElse(missing.dependsOn(), List.<String>of())
                .stream()
                .map(dependencyTitle -> addEdge(tasks, missing.task(), dependencyTitle)))
            .flatMap(Optional::stream)
            .toList();
    }

    private static Optional<String> addEdge(
            final List<Task> tasks, final String taskTitle, final String dependencyTitle) {
        var task = byTitle(tasks, taskTitle);
        var dependency = byTitle(tasks, dependencyTitle);
        Optional<String> added = Optional.empty();
        if (task.isPresent() && dependency.isPresent()
                && canAdd(tasks, task.get(), dependency.get())) {
            link(task.get(), dependency.get());
            added = Optional.of("'" + task.get().getTitle() + "' now depends on '"
                + dependency.get().getTitle() + "'");
        }
        return added;
    }

    private static Optional<Task> byTitle(final List<Task> tasks, final String title) {
        return tasks.stream()
            .filter(task -> TaskTitleMatcher.matches(task.getTitle(), title))
            .findFirst();
    }

    private static boolean canAdd(final List<Task> tasks, final Task task, final Task dependency) {
        return task != dependency
            && !task.getDependsOnTaskIds().contains(dependency.getId())
            && !dependsTransitively(tasks, dependency, task);
    }

    /**
     * Whether {@code from} already depends, directly or transitively, on {@code target} -- in
     * which case making {@code target} depend on {@code from} would close a cycle.
     */
    private static boolean dependsTransitively(
            final List<Task> tasks, final Task from, final Task target) {
        Map<String, Task> byId = tasks.stream()
            .filter(task -> task.getId() != null)
            .collect(Collectors.toMap(Task::getId, Function.identity(), (first, second) -> first));
        var visited = new HashSet<String>();
        var frontier = new ArrayDeque<>(from.getDependsOnTaskIds());
        var found = false;
        while (!frontier.isEmpty() && !found) {
            var id = frontier.pop();
            found = id.equals(target.getId());
            if (visited.add(id) && byId.containsKey(id)) {
                frontier.addAll(byId.get(id).getDependsOnTaskIds());
            }
        }
        return found;
    }

    private static void link(final Task task, final Task dependency) {
        var dependsOn = new ArrayList<>(task.getDependsOnTaskIds());
        dependsOn.add(dependency.getId());
        task.setDependsOnTaskIds(dependsOn);
    }
}
