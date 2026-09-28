import { TaskAttempt } from '../task-attempt.model';

/** A DAG node's derived display status — never persisted, recomputed from the task's attempts. */
export type TaskNodeStatus = 'inactive' | 'active' | 'succeeded' | 'failed';

/**
 * Derives a task's node status from its attempt history (most recent attempt first, per
 * {@link InterfaceWorkflowUpdatesStrategy.taskAttempts}).
 *
 * @param attempts the task's attempts, most recent first
 * @returns `inactive` if never attempted, or the latest attempt is only queued
 *     (`PENDING`/`DISPATCHED`) rather than actually executing; `active` only once it is truly
 *     `RUNNING`; `succeeded`/`failed` once it has settled
 */
export function deriveNodeStatus(attempts: readonly TaskAttempt[]): TaskNodeStatus {
  const latest = attempts[0];
  if (!latest) return 'inactive';
  if (latest.status === 'SUCCEEDED') return 'succeeded';
  if (latest.status === 'RUNNING') return 'active';
  if (latest.status === 'PENDING' || latest.status === 'DISPATCHED') return 'inactive';
  return 'failed';
}
