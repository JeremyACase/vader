/** Equivalent of org.vader.common.model.vader.entity.TaskUpdateType. */
export type TaskUpdateType = 'CREATED' | 'RUNNING' | 'UPDATE' | 'COMPLETED' | 'FAILED'
  | 'TIMED_OUT';

/** Equivalent of org.vader.common.model.vader.entity.TaskUpdateAuthor. */
export type TaskUpdateAuthor = 'SYSTEM' | 'TASK_AGENT' | 'ORCHESTRATOR' | 'EVALUATOR';

/** Equivalent of org.vader.common.model.vader.dto.TaskUpdate. */
export interface TaskUpdate {
  id?: string;
  createdAt?: string;
  taskId: string;
  taskAttemptId?: string;
  type: TaskUpdateType;
  description: string;
  author: TaskUpdateAuthor;
}
