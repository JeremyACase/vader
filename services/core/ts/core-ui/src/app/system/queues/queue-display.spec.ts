import {
  asLlmRequest,
  clientPromptIdOf,
  deferredUntilOf,
  durationBetween,
  inPipelineOrder,
  lifecycleSteps,
  prettyJson,
  subjectLabel,
  taskAttemptIdOf,
  totalMessages
} from './queue-display';
import { OutboxMessageStatus, QueueMessage, QueueSummary } from './queue.model';

function summary(name: string, counts: Partial<Record<OutboxMessageStatus, number>> = {}): QueueSummary {
  return {
    name,
    maxOpenMessages: 1,
    statusCounts: { PENDING: 0, CLAIMED: 0, PROCESSED: 0, FAILED: 0, ...counts }
  };
}

function promptMessage(overrides: Partial<QueueMessage> = {}): QueueMessage {
  return {
    modelType: 'ClientPromptOutboxMessage',
    id: 'm-1',
    createdAt: '2026-01-01T00:00:00Z',
    status: 'PENDING',
    attempts: 0,
    clientPromptId: 'prompt-1',
    ...overrides
  } as QueueMessage;
}

describe('queue-display', () => {
  it('orders queues the way work flows through them, unknown queues last', () => {
    const ordered = inPipelineOrder([
      summary('LlmRequest'),
      summary('Mystery'),
      summary('TaskAttemptReview'),
      summary('ClientPrompt'),
      summary('TaskAssignment')
    ]);

    expect(ordered.map((queue) => queue.name)).toEqual([
      'ClientPrompt',
      'TaskAssignment',
      'TaskAttemptReview',
      'LlmRequest',
      'Mystery'
    ]);
  });

  it('totals every status', () => {
    expect(totalMessages(summary('ClientPrompt', { PENDING: 2, PROCESSED: 5, FAILED: 1 }))).toBe(8);
  });

  it('labels an id subject with what it identifies, and a request kind as-is', () => {
    expect(subjectLabel('ClientPrompt', '1a2b3c4d-0000-0000-0000-000000000000')).toBe('prompt 1a2b3c4d');
    expect(subjectLabel('TaskAssignment', 'abcdef01-0000')).toBe('attempt abcdef01');
    expect(subjectLabel('LlmRequest', 'INFERENCE_TURN')).toBe('INFERENCE_TURN');
  });

  it('formats durations at a readable scale', () => {
    expect(durationBetween('2026-01-01T00:00:00.000Z', '2026-01-01T00:00:00.250Z')).toBe('250ms');
    expect(durationBetween('2026-01-01T00:00:00Z', '2026-01-01T00:00:12.4Z')).toBe('12.4s');
    expect(durationBetween('2026-01-01T00:00:00Z', '2026-01-01T00:03:05Z')).toBe('3m 05s');
    expect(durationBetween('2026-01-01T00:00:00Z', null)).toBeNull();
  });

  it('re-indents JSON and passes anything else through', () => {
    expect(prettyJson('{"a":1}')).toBe('{\n  "a": 1\n}');
    expect(prettyJson('not json')).toBe('not json');
    expect(prettyJson(null)).toBe('');
  });

  it('shows a pending message as waiting to be claimed', () => {
    const steps = lifecycleSteps(promptMessage({ status: 'PENDING' }));

    expect(steps.map((step) => step.state)).toEqual(['done', 'current', 'upcoming']);
    expect(steps[1].note).toBe('waiting…');
  });

  it('shows a processed message with how long it waited and ran', () => {
    const steps = lifecycleSteps(
      promptMessage({
        status: 'PROCESSED',
        claimedAt: '2026-01-01T00:00:02Z',
        processedAt: '2026-01-01T00:00:12Z'
      })
    );

    expect(steps.map((step) => step.state)).toEqual(['done', 'done', 'done']);
    expect(steps[1].note).toBe('waited 2.0s');
    expect(steps[2].note).toBe('took 10.0s');
  });

  it('ends a failed message at its last update, marked failed', () => {
    const steps = lifecycleSteps(
      promptMessage({
        status: 'FAILED',
        claimedAt: '2026-01-01T00:00:01Z',
        updatedAt: '2026-01-01T00:00:04Z'
      })
    );

    expect(steps[2]).toEqual({
      label: 'Failed',
      at: '2026-01-01T00:00:04Z',
      note: 'took 3.0s',
      state: 'failed'
    });
  });

  it('reads each payload reference only from the message type that carries it', () => {
    const prompt = promptMessage();
    const review: QueueMessage = {
      modelType: 'TaskAttemptReviewOutboxMessage',
      id: 'm-2',
      createdAt: '2026-01-01T00:00:00Z',
      status: 'PENDING',
      attempts: 1,
      taskAttemptId: 'attempt-1',
      nextAttemptAt: '2026-01-01T00:00:30Z'
    };
    const llm: QueueMessage = {
      modelType: 'LlmRequestOutboxMessage',
      id: 'm-3',
      createdAt: '2026-01-01T00:00:00Z',
      status: 'CLAIMED',
      attempts: 1,
      kind: 'EVALUATION',
      requestJson: '{}'
    };

    expect(clientPromptIdOf(prompt)).toBe('prompt-1');
    expect(clientPromptIdOf(review)).toBeNull();
    expect(taskAttemptIdOf(review)).toBe('attempt-1');
    expect(taskAttemptIdOf(llm)).toBeNull();
    expect(asLlmRequest(llm)).toBe(llm);
    expect(asLlmRequest(prompt)).toBeNull();
    expect(deferredUntilOf(review)).toBe('2026-01-01T00:00:30Z');
    expect(deferredUntilOf(prompt)).toBeNull();
  });
});
