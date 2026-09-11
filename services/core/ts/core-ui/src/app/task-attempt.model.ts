/** Equivalent of org.vader.common.model.vader.entity.TaskAttemptStatus. */
export type TaskAttemptStatus =
  | 'PENDING'
  | 'DISPATCHED'
  | 'RUNNING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'TIMED_OUT'
  | 'STALLED';

/** Equivalent of org.vader.common.model.vader.dto.TaskAttempt. */
export interface TaskAttempt {
  id?: string;
  taskId: string;
  attemptNumber: number;
  status: TaskAttemptStatus;
  dispatchedAt?: string;
  startedAt?: string;
  lastHeartbeatAt?: string;
  completedAt?: string;
  turnsUsed: number;
  tokensUsed: number;
  result?: string;
  failureReason?: string;
}

/** Equivalent of org.vader.common.model.vader.dto.TaskAttemptTranscript. */
export interface TaskAttemptTranscript {
  id?: string;
  taskAttemptId: string;
  turnIndex: number;
  prompt: string;
  response: string;
  tokensSpent: number;
}
