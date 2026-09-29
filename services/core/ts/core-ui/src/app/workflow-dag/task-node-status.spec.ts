import { Task } from '../client-prompt.model';
import { TaskAttempt } from '../task-attempt.model';
import { TaskUpdate, TaskUpdateType } from '../task-update.model';
import { TaskNodeStatus, applySubtaskGate, deriveOwnStatus } from './task-node-status';

function attempt(overrides: Partial<TaskAttempt>): TaskAttempt {
  return {
    id: 'a1',
    taskId: 't1',
    attemptNumber: 1,
    status: 'RUNNING',
    turnsUsed: 0,
    tokensUsed: 0,
    ...overrides
  };
}

function verdict(type: TaskUpdateType, taskAttemptId = 'a1'): TaskUpdate {
  return { taskId: 't1', taskAttemptId, type, description: 'reasoning', author: 'EVALUATOR' };
}

function task(id: string, subTasks: Task[] = [], spawnedByAttemptId?: string): Task {
  return { id, title: id, description: id, subTasks, dependsOnTaskIds: [], spawnedByAttemptId };
}

describe('deriveOwnStatus', () => {
  it('is inactive when the task was never attempted', () => {
    expect(deriveOwnStatus([], [])).toBe('inactive');
  });

  it('is inactive while the first attempt is only queued', () => {
    expect(deriveOwnStatus([attempt({ status: 'PENDING' })], [])).toBe('inactive');
    expect(deriveOwnStatus([attempt({ status: 'DISPATCHED' })], [])).toBe('inactive');
  });

  it('is active while an attempt runs, or while a retry is queued', () => {
    expect(deriveOwnStatus([attempt({ status: 'RUNNING' })], [])).toBe('active');
    expect(deriveOwnStatus([attempt({ attemptNumber: 2, status: 'PENDING' })], [])).toBe('active');
  });

  it('stays active while a finished attempt awaits its verdict, whatever it self-reported', () => {
    expect(deriveOwnStatus([attempt({ status: 'SUCCEEDED' })], [])).toBe('active');
    expect(deriveOwnStatus([attempt({ status: 'FAILED' })], [])).toBe('active');
  });

  it("follows the evaluator's verdict rather than the self-reported status", () => {
    expect(deriveOwnStatus([attempt({ status: 'SUCCEEDED' })], [verdict('FAILED')])).toBe('failed');
    expect(deriveOwnStatus([attempt({ status: 'FAILED' })], [verdict('COMPLETED')])).toBe(
      'succeeded'
    );
  });

  it('treats a timeout verdict as failed and a decomposition as still in progress', () => {
    expect(deriveOwnStatus([attempt({ status: 'STALLED' })], [verdict('TIMED_OUT')])).toBe(
      'failed'
    );
    expect(deriveOwnStatus([attempt({ status: 'SUCCEEDED' })], [verdict('DECOMPOSED')])).toBe(
      'active'
    );
  });

  it('uses the most recent verdict on the latest attempt only', () => {
    const updates = [verdict('COMPLETED'), verdict('DECOMPOSED'), verdict('FAILED', 'older')];

    expect(deriveOwnStatus([attempt({ status: 'SUCCEEDED' })], updates)).toBe('succeeded');
  });

  it('ignores non-verdict updates', () => {
    const updates = [verdict('UPDATE'), verdict('RUNNING')];

    expect(deriveOwnStatus([attempt({ status: 'SUCCEEDED' })], updates)).toBe('active');
  });
});

describe('applySubtaskGate', () => {
  function statuses(entries: [string, TaskNodeStatus][]): Map<string, TaskNodeStatus> {
    return new Map(entries);
  }

  it('holds a settled parent open while a runtime subtask is still unsettled', () => {
    const roots = [task('p', [task('c1', [], 'a1'), task('c2', [], 'a1')])];
    const own = statuses([['p', 'succeeded'], ['c1', 'succeeded'], ['c2', 'active']]);

    expect(applySubtaskGate(roots, own).get('p')).toBe('active');
  });

  it('holds a failed parent open too, until its subtasks settle', () => {
    const roots = [task('p', [task('c1', [], 'a1')])];

    expect(applySubtaskGate(roots, statuses([['p', 'failed'], ['c1', 'inactive']])).get('p')).toBe(
      'active'
    );
  });

  it('shows the parent verdict once every runtime subtask has settled', () => {
    const roots = [task('p', [task('c1', [], 'a1'), task('c2', [], 'a1')])];
    const own = statuses([['p', 'failed'], ['c1', 'succeeded'], ['c2', 'failed']]);

    expect(applySubtaskGate(roots, own).get('p')).toBe('failed');
  });

  it('gates recursively through nested runtime subtasks', () => {
    const roots = [task('p', [task('c', [task('g', [], 'ca')], 'a1')])];
    const own = statuses([['p', 'succeeded'], ['c', 'succeeded'], ['g', 'active']]);

    const result = applySubtaskGate(roots, own);

    expect(result.get('c')).toBe('active');
    expect(result.get('p')).toBe('active');
  });

  it('never lets subtasks the plan itself nested hold the parent open', () => {
    const roots = [task('p', [task('wireframe')])];
    const own = statuses([['p', 'succeeded']]);

    expect(applySubtaskGate(roots, own).get('p')).toBe('succeeded');
  });

  it('leaves unsettled parents and tasks without subtasks as they are', () => {
    const roots = [task('p', [task('c1', [], 'a1')]), task('solo')];
    const own = statuses([['p', 'active'], ['c1', 'active'], ['solo', 'failed']]);

    const result = applySubtaskGate(roots, own);

    expect(result.get('p')).toBe('active');
    expect(result.get('solo')).toBe('failed');
    expect(result.get('c1')).toBe('active');
  });
});
