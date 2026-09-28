/** One task rendered as a DAG node, positioned in an SVG coordinate space. */
export interface DagNode {
  taskId: string;
  title: string;
  x: number;
  y: number;
}

/** One `dependsOnTaskIds` edge, drawn from the dependency to the dependent task. */
export interface DagEdge {
  fromTaskId: string;
  toTaskId: string;
}

/** A laid-out task graph, ready to render into an SVG viewBox of exactly `width` by `height`. */
export interface TaskGraphLayout {
  nodes: DagNode[];
  edges: DagEdge[];
  width: number;
  height: number;
}
