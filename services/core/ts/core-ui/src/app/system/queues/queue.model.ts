/** Equivalent of org.vader.common.model.vader.entity.OutboxMessageStatus. */
export type OutboxMessageStatus = 'PENDING' | 'CLAIMED' | 'PROCESSED' | 'FAILED';

/** Every status, in lifecycle order. */
export const OUTBOX_MESSAGE_STATUSES: readonly OutboxMessageStatus[] = [
  'PENDING',
  'CLAIMED',
  'PROCESSED',
  'FAILED'
];

/** Equivalent of org.vader.core.server.messaging.model.QueueSummary. */
export interface QueueSummary {
  name: string;
  maxOpenMessages: number;
  statusCounts: Record<OutboxMessageStatus, number>;
}

/** Equivalent of org.vader.core.server.messaging.model.QueueMessageSummary: a list row. */
export interface QueueMessageSummary {
  id: string;
  modelType: string;
  status: OutboxMessageStatus;
  createdAt: string;
  claimedAt?: string | null;
  processedAt?: string | null;
  attempts: number;
  /** A request kind (LLM queue) or the id of the payload the message carries. */
  subject?: string | null;
}

/** Equivalent of org.vader.core.server.messaging.model.QueueMessagePage. */
export interface QueueMessagePage {
  content: QueueMessageSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Equivalent of org.vader.common.model.vader.dto.AbstractOutboxMessage. */
interface AbstractOutboxMessage {
  id: string;
  createdAt: string;
  updatedAt?: string | null;
  status: OutboxMessageStatus;
  claimedAt?: string | null;
  processedAt?: string | null;
  attempts: number;
  failureReason?: string | null;
}

/** Equivalent of org.vader.common.model.vader.dto.ClientPromptOutboxMessage. */
export interface ClientPromptOutboxMessage extends AbstractOutboxMessage {
  modelType: 'ClientPromptOutboxMessage';
  clientPromptId?: string | null;
}

/** Equivalent of org.vader.common.model.vader.dto.TaskAssignmentOutboxMessage. */
export interface TaskAssignmentOutboxMessage extends AbstractOutboxMessage {
  modelType: 'TaskAssignmentOutboxMessage';
  taskAttemptId?: string | null;
}

/** Equivalent of org.vader.common.model.vader.dto.TaskAttemptReviewOutboxMessage. */
export interface TaskAttemptReviewOutboxMessage extends AbstractOutboxMessage {
  modelType: 'TaskAttemptReviewOutboxMessage';
  taskAttemptId?: string | null;
  /** Set while a review deferred by an LLM outage waits to be claimed again. */
  nextAttemptAt?: string | null;
}

/** Equivalent of org.vader.common.model.vader.entity.LlmRequestKind. */
export type LlmRequestKind =
  | 'INFERENCE_TURN'
  | 'DECOMPOSITION'
  | 'EVALUATION'
  | 'REATTEMPT_DECISION'
  | 'TASK_PLAN_REFINEMENT'
  | 'WORKFLOW_SYNTHESIS';

/** Equivalent of org.vader.common.model.vader.dto.LlmRequestOutboxMessage. */
export interface LlmRequestOutboxMessage extends AbstractOutboxMessage {
  modelType: 'LlmRequestOutboxMessage';
  kind: LlmRequestKind;
  requestJson: string;
  responseJson?: string | null;
}

/** One queue message in full, discriminated by `modelType`. */
export type QueueMessage =
  | ClientPromptOutboxMessage
  | TaskAssignmentOutboxMessage
  | TaskAttemptReviewOutboxMessage
  | LlmRequestOutboxMessage;
