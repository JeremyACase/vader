import { PendingWorkflowRegistry } from './pending-workflow.registry';

describe('PendingWorkflowRegistry', () => {
  let registry: PendingWorkflowRegistry;

  beforeEach(() => {
    registry = new PendingWorkflowRegistry();
  });

  it('starts with nothing pending', () => {
    expect(registry.pending()).toBeNull();
  });

  it('registers a pending entry', () => {
    registry.register('prompt-1');

    const entry = registry.pending();
    expect(entry?.clientPromptId).toBe('prompt-1');
    expect(entry?.submittedAt).toBeGreaterThan(0);
  });

  it('registering a new prompt replaces the previous one', () => {
    registry.register('prompt-1');
    registry.register('prompt-2');

    expect(registry.pending()?.clientPromptId).toBe('prompt-2');
  });

  it('resolve clears the pending entry when the id matches', () => {
    registry.register('prompt-1');
    registry.resolve('prompt-1');

    expect(registry.pending()).toBeNull();
  });

  it('resolve is a no-op when the id does not match the current pending entry', () => {
    registry.register('prompt-1');
    registry.resolve('some-other-prompt');

    expect(registry.pending()?.clientPromptId).toBe('prompt-1');
  });
});
