import { TaskAttempt } from '../task-attempt.model';
import { deriveNodeStatus } from './task-node-status';

function attempt(overrides: Partial<TaskAttempt>): TaskAttempt {
  return { taskId: 't1', attemptNumber: 1, status: 'RUNNING', turnsUsed: 0, tokensUsed: 0, ...overrides };
}

describe('deriveNodeStatus', () => {
  it('returns inactive when there are no attempts', () => {
    expect(deriveNodeStatus([])).toBe('inactive');
  });

  it('returns active for the open statuses', () => {
    expect(deriveNodeStatus([attempt({ status: 'PENDING' })])).toBe('active');
    expect(deriveNodeStatus([attempt({ status: 'DISPATCHED' })])).toBe('active');
    expect(deriveNodeStatus([attempt({ status: 'RUNNING' })])).toBe('active');
  });

  it('returns succeeded when the latest attempt succeeded', () => {
    expect(deriveNodeStatus([attempt({ status: 'SUCCEEDED' })])).toBe('succeeded');
  });

  it('returns failed for failed, timed-out, or stalled latest attempts', () => {
    expect(deriveNodeStatus([attempt({ status: 'FAILED' })])).toBe('failed');
    expect(deriveNodeStatus([attempt({ status: 'TIMED_OUT' })])).toBe('failed');
    expect(deriveNodeStatus([attempt({ status: 'STALLED' })])).toBe('failed');
  });

  it('derives from only the most recent attempt, first in the array', () => {
    const attempts = [attempt({ status: 'RUNNING' }), attempt({ status: 'FAILED' })];

    expect(deriveNodeStatus(attempts)).toBe('active');
  });
});
