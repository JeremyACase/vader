import { Task } from '../client-prompt.model';
import { TaskAttempt, TaskAttemptStatus } from '../task-attempt.model';
import { TaskUpdate, TaskUpdateType } from '../task-update.model';

/** A DAG node's derived display status — never persisted, recomputed from the task's history. */
export type TaskNodeStatus = 'inactive' | 'active' | 'succeeded' | 'failed';

/** The update types core-server's review pipeline records as a verdict on one attempt. */
const VERDICT_TYPES: ReadonlySet<TaskUpdateType> = new Set<TaskUpdateType>([
  'COMPLETED',
  'FAILED',
  'TIMED_OUT',
  'DECOMPOSED'
]);

/** Attempt statuses that mean the harness has not reported back yet. */
const OPEN_STATUSES: ReadonlySet<TaskAttemptStatus> = new Set<TaskAttemptStatus>([
  'PENDING',
  'DISPATCHED',
  'RUNNING'
]);

const VERDICT_TO_STATUS: Readonly<Record<string, TaskNodeStatus>> = {
  COMPLETED: 'succeeded',
  FAILED: 'failed',
  TIMED_OUT: 'failed',
  // Its remaining work is running as subtasks: the task itself is still in progress.
  DECOMPOSED: 'active'
};

/**
 * Derives one task's own status from its latest attempt and the evaluator's verdict on it —
 * never the harness's self-reported outcome, which an agent can get wrong in either direction.
 *
 * @param attempts the task's attempts, most recent first
 * @param updates the task's updates, most recent first
 * @returns `inactive` until the task has actually started (never attempted, or its first attempt
 *     is only queued); `active` while an attempt is queued as a retry or running, while a
 *     finished attempt awaits its verdict, or once it was decomposed into subtasks;
 *     `succeeded`/`failed` from the latest attempt's verdict
 */
export function deriveOwnStatus(
  attempts: readonly TaskAttempt[],
  updates: readonly TaskUpdate[]
): TaskNodeStatus {
  const latest = attempts[0];
  let status: TaskNodeStatus;
  if (!latest || isQueuedFirstAttempt(latest)) {
    status = 'inactive';
  } else if (OPEN_STATUSES.has(latest.status)) {
    status = 'active';
  } else {
    status = verdictStatus(latest, updates);
  }
  return status;
}

/**
 * Applies the subtask rule on top of every task's own status: a task whose own verdict is a pass
 * or fail still reads as `active` until every subtask spawned from it at runtime has itself
 * settled as a pass or fail (recursively). Subtasks a plan nested up front are never scheduled,
 * so they never hold their parent open.
 *
 * @param roots the workflow's root tasks
 * @param ownStatuses each task's own status, by task id
 * @returns each task's display status, by task id
 */
export function applySubtaskGate(
  roots: readonly Task[],
  ownStatuses: ReadonlyMap<string, TaskNodeStatus>
): Map<string, TaskNodeStatus> {
  const result = new Map<string, TaskNodeStatus>();
  roots.forEach((root) => gate(root, ownStatuses, result));
  return result;
}

function gate(
  task: Task,
  ownStatuses: ReadonlyMap<string, TaskNodeStatus>,
  result: Map<string, TaskNodeStatus>
): TaskNodeStatus {
  const runtimeSubtaskStatuses = task.subTasks
    .map((subtask) => ({ subtask, status: gate(subtask, ownStatuses, result) }))
    .filter(({ subtask }) => !!subtask.spawnedByAttemptId)
    .map(({ status }) => status);
  const own = (task.id && ownStatuses.get(task.id)) || 'inactive';
  const status = isSettled(own) && !runtimeSubtaskStatuses.every(isSettled) ? 'active' : own;
  if (task.id) {
    result.set(task.id, status);
  }
  return status;
}

function isSettled(status: TaskNodeStatus): boolean {
  return status === 'succeeded' || status === 'failed';
}

function isQueuedFirstAttempt(attempt: TaskAttempt): boolean {
  return attempt.attemptNumber <= 1 && (attempt.status === 'PENDING' || attempt.status === 'DISPATCHED');
}

/** A finished attempt with no verdict yet is still under review, so still in progress. */
function verdictStatus(attempt: TaskAttempt, updates: readonly TaskUpdate[]): TaskNodeStatus {
  const verdict = updates.find(
    (update) => update.taskAttemptId === attempt.id && VERDICT_TYPES.has(update.type)
  );
  return verdict ? VERDICT_TO_STATUS[verdict.type] : 'active';
}
