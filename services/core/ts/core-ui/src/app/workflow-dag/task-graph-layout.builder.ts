import { Task } from '../client-prompt.model';
import { DagEdge, DagNode, TaskGraphLayout } from './task-graph-layout.model';

const COLUMN_WIDTH = 220;
const ROW_HEIGHT = 130;
const MARGIN = 110;

type IdentifiedTask = Task & { id: string };

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
 * Builder that lays out an already-flattened task list as a bottom-to-top DAG.
 *
 * <p>Tasks are grouped into bands by depth: the plan's own (root) tasks form the top band, the
 * subtasks decomposed from them the band beneath it, and so on -- so decomposing a task only ever
 * adds rows below the graph, never reshuffles what is already drawn. Within a band, a task's row
 * is one below the lowest of the tasks that depend on it: the "final" tasks nothing depends on sit
 * at the top, and every dependency edge points upward from a prerequisite to its dependent. Tasks
 * sharing a row sit side by side in list order, each row centred on a common axis.</p>
 *
 * <p>Deliberately does not attempt edge-crossing minimization — these are small graphs (a
 * handful of tasks per workflow, plus at most a few subtasks each), where a simple layered layout
 * reads just as clearly as a more sophisticated one would.</p>
 */
export class TaskGraphLayoutBuilder {
  static build(tasks: readonly Task[]): TaskGraphLayout {
    const identified = tasks.filter((task): task is IdentifiedTask => !!task.id);
    const byId = new Map(identified.map((task) => [task.id, task] as const));
    const parentById = TaskGraphLayoutBuilder.parentsOf(identified);
    const depthById = TaskGraphLayoutBuilder.computeDepths(byId, parentById);
    const layerById = TaskGraphLayoutBuilder.computeLayers(byId, depthById);
    const rowById = TaskGraphLayoutBuilder.computeRows(layerById, depthById);
    const nodes = TaskGraphLayoutBuilder.positionNodes(identified, rowById);
    const edges = TaskGraphLayoutBuilder.dependencyEdges(byId);
    return {
      nodes,
      edges,
      width: TaskGraphLayoutBuilder.width(nodes),
      height: TaskGraphLayoutBuilder.height(nodes)
    };
  }

  /** Each subtask's parent id, taken from the `subTasks` nesting itself. */
  private static parentsOf(tasks: readonly IdentifiedTask[]): Map<string, string> {
    const parentById = new Map<string, string>();
    tasks.forEach((task) =>
      task.subTasks
        .filter((subtask): subtask is IdentifiedTask => !!subtask.id)
        .forEach((subtask) => parentById.set(subtask.id, task.id))
    );
    return parentById;
  }

  private static computeDepths(
    byId: ReadonlyMap<string, IdentifiedTask>,
    parentById: ReadonlyMap<string, string>
  ): Map<string, number> {
    const depthOf = (taskId: string, seen: number): number => {
      const parentId = parentById.get(taskId);
      // `seen` caps the walk: a cyclic parent chain should never reach the UI.
      return parentId && seen < byId.size ? depthOf(parentId, seen + 1) + 1 : 0;
    };
    return new Map(Array.from(byId.keys(), (taskId) => [taskId, depthOf(taskId, 0)] as const));
  }

  /**
   * A task's layer within its own band: 0 when nothing in that band depends on it, otherwise one
   * more than the deepest layer among its dependents -- so prerequisites sit below what needs them.
   */
  private static computeLayers(
    byId: ReadonlyMap<string, IdentifiedTask>,
    depthById: ReadonlyMap<string, number>
  ): Map<string, number> {
    const dependentsById = TaskGraphLayoutBuilder.sameBandDependents(byId, depthById);
    const layerById = new Map<string, number>();
    const resolving = new Set<string>();

    const layerOf = (taskId: string): number => {
      const cached = layerById.get(taskId);
      if (cached !== undefined) return cached;
      if (resolving.has(taskId)) return 0; // defensive only: a cycle should never reach the UI

      resolving.add(taskId);
      const dependents = dependentsById.get(taskId) ?? [];
      const layer = dependents.length ? Math.max(...dependents.map(layerOf)) + 1 : 0;
      resolving.delete(taskId);
      layerById.set(taskId, layer);
      return layer;
    };

    byId.forEach((_task, taskId) => layerOf(taskId));
    return layerById;
  }

  private static sameBandDependents(
    byId: ReadonlyMap<string, IdentifiedTask>,
    depthById: ReadonlyMap<string, number>
  ): Map<string, string[]> {
    const dependentsById = new Map<string, string[]>();
    byId.forEach((task, taskId) =>
      task.dependsOnTaskIds
        .filter((dependencyId) => depthById.get(dependencyId) === depthById.get(taskId))
        .forEach((dependencyId) =>
          dependentsById.set(dependencyId, [...(dependentsById.get(dependencyId) ?? []), taskId])
        )
    );
    return dependentsById;
  }

  /** Stacks the bands top to bottom: a task's row is its band's first row plus its layer. */
  private static computeRows(
    layerById: ReadonlyMap<string, number>,
    depthById: ReadonlyMap<string, number>
  ): Map<string, number> {
    const bandHeights: number[] = [];
    layerById.forEach((layer, taskId) => {
      const depth = depthById.get(taskId) ?? 0;
      bandHeights[depth] = Math.max(bandHeights[depth] ?? 0, layer + 1);
    });
    const bandOffsets = TaskGraphLayoutBuilder.cumulativeOffsets(bandHeights);
    return new Map(
      Array.from(layerById, ([taskId, layer]) => {
        const depth = depthById.get(taskId) ?? 0;
        return [taskId, bandOffsets[depth] + layer] as const;
      })
    );
  }

  private static cumulativeOffsets(bandHeights: readonly (number | undefined)[]): number[] {
    const offsets: number[] = [];
    Array.from(bandHeights, (height) => height ?? 0).reduce((offset, height, depth) => {
      offsets[depth] = offset;
      return offset + height;
    }, 0);
    return offsets;
  }

  private static positionNodes(
    tasks: readonly IdentifiedTask[],
    rowById: ReadonlyMap<string, number>
  ): DagNode[] {
    const tasksByRow = new Map<number, IdentifiedTask[]>();
    tasks.forEach((task) => {
      const row = rowById.get(task.id) ?? 0;
      tasksByRow.set(row, [...(tasksByRow.get(row) ?? []), task]);
    });
    const centered = tasks.map((task) => {
      const row = rowById.get(task.id) ?? 0;
      const rowTasks = tasksByRow.get(row) ?? [task];
      const offset = rowTasks.indexOf(task) - (rowTasks.length - 1) / 2;
      return { taskId: task.id, title: task.title, x: offset * COLUMN_WIDTH, y: row * ROW_HEIGHT };
    });
    const minX = Math.min(0, ...centered.map((node) => node.x));
    return centered.map((node) => ({ ...node, x: node.x - minX + MARGIN, y: node.y + MARGIN }));
  }

  private static dependencyEdges(byId: ReadonlyMap<string, IdentifiedTask>): DagEdge[] {
    const edges: DagEdge[] = [];
    byId.forEach((task, taskId) =>
      task.dependsOnTaskIds
        .filter((dependencyId) => byId.has(dependencyId))
        .forEach((dependencyId) =>
          edges.push({ fromTaskId: dependencyId, toTaskId: taskId })
        )
    );
    return edges;
  }

  private static width(nodes: readonly DagNode[]): number {
    const maxX = Math.max(0, ...nodes.map((node) => node.x));
    return maxX + MARGIN;
  }

  private static height(nodes: readonly DagNode[]): number {
    const maxY = Math.max(0, ...nodes.map((node) => node.y));
    return maxY + MARGIN;
  }
}
