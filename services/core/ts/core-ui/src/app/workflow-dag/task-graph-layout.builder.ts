import { Task } from '../client-prompt.model';
import { DagEdge, DagNode, TaskGraphLayout } from './task-graph-layout.model';

const COLUMN_WIDTH = 220;
const ROW_HEIGHT = 90;
const MARGIN = 110;

/** Recursively flattens a task tree (root tasks plus their nested `subTasks`) into one list. */
export function flattenTasks(tasks: readonly Task[]): Task[] {
  const flat: Task[] = [];
  const visit = (task: Task): void => {
    flat.push(task);
    task.subTasks.forEach(visit);
  };
  tasks.forEach(visit);
  return flat;
}

/**
 * Builder that lays out an already-flattened task list as a DAG: a task's column (layer) is one
 * more than the deepest layer among its own `dependsOnTaskIds`, so every dependency edge points
 * strictly left-to-right; tasks sharing a layer stack top to bottom in list order.
 *
 * <p>Deliberately does not attempt edge-crossing minimization — these are small graphs (a
 * handful of tasks per workflow, per the decomposition prompt's own 2-6 top-level task guidance),
 * where a simple layered layout reads just as clearly as a more sophisticated one would.</p>
 */
export class TaskGraphLayoutBuilder {
  static build(tasks: readonly Task[]): TaskGraphLayout {
    const byId = TaskGraphLayoutBuilder.indexById(tasks);
    const layerById = TaskGraphLayoutBuilder.computeLayers(byId);
    const nodes = TaskGraphLayoutBuilder.positionNodes(tasks, layerById);
    const edges = TaskGraphLayoutBuilder.buildEdges(byId);
    return {
      nodes,
      edges,
      width: TaskGraphLayoutBuilder.width(layerById),
      height: TaskGraphLayoutBuilder.height(nodes)
    };
  }

  private static indexById(tasks: readonly Task[]): ReadonlyMap<string, Task> {
    const idAndTask = tasks
      .filter((task): task is Task & { id: string } => !!task.id)
      .map((task) => [task.id, task] as const);
    return new Map(idAndTask);
  }

  private static computeLayers(byId: ReadonlyMap<string, Task>): Map<string, number> {
    const layerById = new Map<string, number>();
    const resolving = new Set<string>();

    const layerOf = (taskId: string): number => {
      const cached = layerById.get(taskId);
      if (cached !== undefined) return cached;
      if (resolving.has(taskId)) return 0; // defensive only: a cycle should never reach the UI

      resolving.add(taskId);
      const dependsOn = (byId.get(taskId)?.dependsOnTaskIds ?? []).filter((id) => byId.has(id));
      const layer = dependsOn.length ? Math.max(...dependsOn.map(layerOf)) + 1 : 0;
      resolving.delete(taskId);
      layerById.set(taskId, layer);
      return layer;
    };

    byId.forEach((_task, taskId) => layerOf(taskId));
    return layerById;
  }

  private static positionNodes(
    tasks: readonly Task[],
    layerById: ReadonlyMap<string, number>
  ): DagNode[] {
    const rowByLayer = new Map<number, number>();
    return tasks
      .filter((task): task is Task & { id: string } => !!task.id)
      .map((task) => {
        const layer = layerById.get(task.id) ?? 0;
        const row = rowByLayer.get(layer) ?? 0;
        rowByLayer.set(layer, row + 1);
        return {
          taskId: task.id,
          title: task.title,
          x: MARGIN + layer * COLUMN_WIDTH,
          y: MARGIN + row * ROW_HEIGHT
        };
      });
  }

  private static buildEdges(byId: ReadonlyMap<string, Task>): DagEdge[] {
    const edges: DagEdge[] = [];
    byId.forEach((task, taskId) => {
      task.dependsOnTaskIds
        .filter((dependencyId) => byId.has(dependencyId))
        .forEach((dependencyId) => edges.push({ fromTaskId: dependencyId, toTaskId: taskId }));
    });
    return edges;
  }

  private static width(layerById: ReadonlyMap<string, number>): number {
    const maxLayer = Math.max(0, ...Array.from(layerById.values()));
    return MARGIN * 2 + maxLayer * COLUMN_WIDTH;
  }

  private static height(nodes: readonly DagNode[]): number {
    const maxY = Math.max(0, ...nodes.map((node) => node.y));
    return maxY + MARGIN;
  }
}
