import {
  LlmRequestOutboxMessage,
  OutboxMessageStatus,
  QueueMessage,
  QueueSummary
} from './queue.model';

/** How the UI presents one queue: a readable name, what flows through it, its place in the
 *  pipeline, and what a message's `subject` refers to. */
export interface QueueDisplay {
  label: string;
  description: string;
  order: number;
  subjectNoun: string | null;
}

const KNOWN_QUEUES: Readonly<Record<string, QueueDisplay>> = {
  ClientPrompt: {
    label: 'Client prompts',
    description: 'Submitted prompts waiting to be decomposed into a task plan.',
    order: 0,
    subjectNoun: 'prompt'
  },
  TaskAssignment: {
    label: 'Task assignments',
    description: 'Task attempts waiting to be dispatched to an agent harness Job.',
    order: 1,
    subjectNoun: 'attempt'
  },
  TaskAttemptReview: {
    label: 'Attempt reviews',
    description: 'Settled attempts waiting for an evaluator verdict and reattempt decision.',
    order: 2,
    subjectNoun: 'attempt'
  },
  LlmRequest: {
    label: 'LLM requests',
    description: 'Every call to the local LLM, performed strictly one at a time.',
    order: 3,
    subjectNoun: null
  }
};

/** Display details for a queue; an unknown queue falls back to its raw name, sorted last. */
export function queueDisplay(name: string): QueueDisplay {
  const fallback: QueueDisplay = {
    label: name,
    description: '',
    order: Number.MAX_SAFE_INTEGER,
    subjectNoun: null
  };
  return KNOWN_QUEUES[name] ?? fallback;
}

/** Queues in pipeline order: prompt intake first, the shared LLM queue last. */
export function inPipelineOrder(queues: readonly QueueSummary[]): QueueSummary[] {
  return [...queues].sort((a, b) => queueDisplay(a.name).order - queueDisplay(b.name).order);
}

/** Every message the queue holds, across all statuses. */
export function totalMessages(queue: QueueSummary): number {
  return Object.values(queue.statusCounts).reduce((sum, count) => sum + count, 0);
}

/** A list row's subject: an id prefixed with what it identifies, or a request kind as-is. */
export function subjectLabel(queueName: string, subject?: string | null): string {
  const noun = queueDisplay(queueName).subjectNoun;
  return noun ? `${noun} ${shortId(subject)}` : (subject ?? '—');
}

/** What each status means for a message, in plain words. */
export const STATUS_MEANING: Readonly<Record<OutboxMessageStatus, string>> = {
  PENDING: 'Waiting to be claimed',
  CLAIMED: 'Being processed',
  PROCESSED: 'Processed successfully',
  FAILED: 'Processing failed'
};

/** Whether a message can still change: it has not been processed or failed yet. */
export function isOpen(status: OutboxMessageStatus): boolean {
  return status === 'PENDING' || status === 'CLAIMED';
}

/** One step of a message's lifecycle as drawn in its timeline. */
export interface LifecycleStep {
  label: string;
  at: string | null;
  note: string | null;
  state: 'done' | 'current' | 'upcoming' | 'failed';
}

const SETTLED_STATE: Readonly<Record<OutboxMessageStatus, LifecycleStep['state']>> = {
  PENDING: 'upcoming',
  CLAIMED: 'current',
  PROCESSED: 'done',
  FAILED: 'failed'
};

/** Enqueued, claimed, then processed or failed, each with when it happened and how long the
 *  message spent getting there. */
export function lifecycleSteps(message: QueueMessage): LifecycleStep[] {
  return [
    { label: 'Enqueued', at: message.createdAt, note: null, state: 'done' },
    claimedStep(message),
    settledStep(message)
  ];
}

function claimedStep(message: QueueMessage): LifecycleStep {
  const isWaiting = message.status === 'PENDING';
  const waited = durationBetween(message.createdAt, message.claimedAt);
  return {
    label: 'Claimed',
    at: isWaiting ? null : (message.claimedAt ?? null),
    note: isWaiting ? 'waiting…' : waited && `waited ${waited}`,
    state: isWaiting ? 'current' : 'done'
  };
}

function settledStep(message: QueueMessage): LifecycleStep {
  const settledAt = settledAtOf(message);
  const took = durationBetween(message.claimedAt, settledAt);
  return {
    label: message.status === 'FAILED' ? 'Failed' : 'Processed',
    at: settledAt,
    note: message.status === 'CLAIMED' ? 'in progress…' : took && `took ${took}`,
    state: SETTLED_STATE[message.status]
  };
}

/** A failed message records no processedAt; the failure is its last update. */
function settledAtOf(message: QueueMessage): string | null {
  const settledAt: Readonly<Record<OutboxMessageStatus, string | null | undefined>> = {
    PENDING: null,
    CLAIMED: null,
    PROCESSED: message.processedAt,
    FAILED: message.updatedAt
  };
  return settledAt[message.status] ?? null;
}

/** The prompt id a client-prompt message carries, if this is one. */
export function clientPromptIdOf(message: QueueMessage): string | null {
  return message.modelType === 'ClientPromptOutboxMessage'
    ? (message.clientPromptId ?? null)
    : null;
}

/** The task attempt id a task-assignment or attempt-review message carries, if this is one. */
export function taskAttemptIdOf(message: QueueMessage): string | null {
  const carriesAttempt =
    message.modelType === 'TaskAssignmentOutboxMessage' ||
    message.modelType === 'TaskAttemptReviewOutboxMessage';
  return carriesAttempt ? (message.taskAttemptId ?? null) : null;
}

/** The message as an LLM request, if it is one. */
export function asLlmRequest(message: QueueMessage): LlmRequestOutboxMessage | null {
  return message.modelType === 'LlmRequestOutboxMessage' ? message : null;
}

/** When a review deferred by an LLM outage may next be claimed, if it is deferred. */
export function deferredUntilOf(message: QueueMessage): string | null {
  const isDeferredReview =
    message.modelType === 'TaskAttemptReviewOutboxMessage' && message.status === 'PENDING';
  return isDeferredReview ? (message.nextAttemptAt ?? null) : null;
}

/** Elapsed time between two instants as e.g. `850ms`, `12.4s`, `3m 05s`; `null` if either is
 *  missing. */
export function durationBetween(from?: string | null, to?: string | null): string | null {
  return from && to ? formatMillis(Date.parse(to) - Date.parse(from)) : null;
}

function formatMillis(millis: number): string {
  const seconds = Math.max(0, millis) / 1000;
  const wholeSeconds = String(Math.floor(seconds % 60)).padStart(2, '0');
  let text = `${Math.floor(seconds / 60)}m ${wholeSeconds}s`;
  if (seconds < 60) {
    text = `${seconds.toFixed(1)}s`;
  }
  if (seconds < 1) {
    text = `${Math.round(seconds * 1000)}ms`;
  }
  return text;
}

/** Re-indents a JSON string for reading; anything that isn't JSON is returned unchanged. */
export function prettyJson(raw?: string | null): string {
  return raw ? tryReindent(raw) : '';
}

function tryReindent(raw: string): string {
  let text = raw;
  try {
    text = JSON.stringify(JSON.parse(raw) as unknown, null, 2);
  } catch {
    // Not JSON: shown verbatim.
  }
  return text;
}

/** The first segment of a UUID: enough to tell rows apart at a glance. */
export function shortId(id?: string | null): string {
  return id ? id.split('-')[0] : '—';
}

