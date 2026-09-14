import { Injectable, signal } from '@angular/core';

/** The prompt currently in flight: accepted by the server but with no Workflow row back yet. */
export interface PendingWorkflow {
  clientPromptId: string;
  submittedAt: number;
}

/**
 * Tracks the single prompt submission currently in flight — the form only allows one at a time,
 * so the panel can render an optimistic placeholder for it the instant it's accepted instead of
 * waiting for decomposition to finish and the next poll to surface the real workflow, and the
 * form can block further submissions until that workflow comes back hydrated.
 */
@Injectable({ providedIn: 'root' })
export class PendingWorkflowRegistry {
  private readonly current = signal<PendingWorkflow | null>(null);

  readonly pending = this.current.asReadonly();

  register(clientPromptId: string): void {
    this.current.set({ clientPromptId, submittedAt: Date.now() });
  }

  resolve(clientPromptId: string): void {
    if (this.current()?.clientPromptId === clientPromptId) {
      this.current.set(null);
    }
  }
}
