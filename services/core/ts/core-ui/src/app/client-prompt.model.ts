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
  reasoning?: string;
  objective: string;
  taskGraph: TaskGraph;
}

/** Equivalent of org.vader.common.model.vader.entity.WorkflowStatus. */
export type WorkflowStatus = 'RUNNING' | 'SUCCEEDED' | 'FAILED';

/**
 * Equivalent of org.vader.common.model.vader.dto.Workflow: the decomposition the server
 * produced for a submitted prompt.
 */
export interface Workflow {
  id?: string;
  createdAt?: string;
  completedAt?: string;
  clientPromptId: string;
  taskPlan?: TaskPlan;
  status: WorkflowStatus;
  /** The single answer synthesized from every task's own result, once the workflow is terminal. */
  result?: string;
}

/**
 * Equivalent of org.vader.common.model.vader.IngressResponse: the receipt the server returns
 * when a prompt has been accepted and queued for decomposition.
 */
export interface IngressResponse {
  id: string;
  modelType: 'IngressResponse';
  payloadModelType: string;
}

/** Equivalent of org.vader.common.model.vader.queue.Ingress. */
export interface Ingress {
  queuedRecords: number;
  queueRatePerMinute: number;
}

/** Equivalent of org.vader.common.model.vader.queue.Egress. */
export interface Egress {
  maxOpenMessages: number;
  currentOpenMessages: number;
}

/**
 * Equivalent of org.vader.common.model.vader.queue.BackPressure: how backed up the
 * client-prompt decomposition queue is.
 */
export interface BackPressure {
  ingress: Ingress;
  egress: Egress;
}

/** Error body returned by the server (ClientPromptController.ErrorResponse). */
export interface OrchestratorError {
  error: string;
  message: string;
}
