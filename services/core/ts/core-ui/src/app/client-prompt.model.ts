/**
 * TypeScript equivalent of org.vader.common.model.vader.dto.ClientPrompt.
 * `id`, `createdAt`, and `updatedAt` are populated by the server
 * (see AbstractModel.java) and are absent on a client-submitted prompt.
 */
export interface ClientPrompt {
  id?: string;
  createdAt?: string;
  updatedAt?: string;
  readonly modelType: 'ClientPrompt';
  text: string;
  files?: File[];
}

/** Equivalent of org.vader.common.model.vader.dto.Task. */
export interface Task {
  id?: string;
  title: string;
  description: string;
  parentTaskId?: string;
  subTasks: Task[];
  dependsOnTaskIds: string[];
}

/** Equivalent of org.vader.common.model.vader.dto.TaskGraph. */
export interface TaskGraph {
  id?: string;
  tasks: Task[];
}

/** Equivalent of org.vader.common.model.vader.dto.TaskPlan. */
export interface TaskPlan {
  id?: string;
  objective: string;
  taskGraph: TaskGraph;
}

/**
 * Equivalent of org.vader.common.model.vader.dto.Workflow: the decomposition the server
 * produced for a submitted prompt.
 */
export interface Workflow {
  id?: string;
  clientPromptId: string;
  taskPlan?: TaskPlan;
}

/** Error body returned by the server (ClientPromptController.ErrorResponse) on a 502. */
export interface OrchestratorError {
  error: string;
  message: string;
}
