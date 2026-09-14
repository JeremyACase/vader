import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActiveWorkflowsService } from '../active-workflows.service';
import { Workflow } from '../client-prompt.model';
import { PendingWorkflow, PendingWorkflowRegistry } from '../workflow-updates/pending-workflow.registry';
import { WorkflowDetailComponent } from './workflow-detail.component';

const PENDING_ID_PREFIX = 'pending:';

/**
 * Persistent panel of the most recent workflows. Shows every recent workflow regardless of
 * status, not just `RUNNING` ones, so a workflow that just failed or succeeded stays visible
 * instead of silently disappearing. Auto-expands the row spawned from `justSubmittedPromptId` the
 * moment it appears.
 *
 * <p>A prompt just accepted by the server has no Workflow row yet — decomposition and the next
 * poll both take a few seconds. Rather than show nothing during that gap, the in-flight entry in
 * {@link PendingWorkflowRegistry} (the form blocks a second submission until it resolves) is
 * rendered as a "Decomposing…" placeholder row until its real Workflow shows up in a poll, at
 * which point the placeholder is dropped in favor of the real row. Expansion state is keyed by
 * `clientPromptId` rather than `workflow.id` specifically so it survives that placeholder-to-real
 * swap.
 */
@Component({
  selector: 'app-workflow-panel',
  standalone: true,
  imports: [WorkflowDetailComponent],
  templateUrl: './workflow-panel.component.html',
  styleUrl: './workflow-panel.component.css'
})
export class WorkflowPanelComponent {
  private activeWorkflowsService = inject(ActiveWorkflowsService);
  private pendingWorkflows = inject(PendingWorkflowRegistry);

  justSubmittedPromptId = input<string | null>(null);

  private workflows = toSignal(this.activeWorkflowsService.recentWorkflows(), {
    initialValue: [] as Workflow[]
  });

  readonly sortedWorkflows = computed(() => {
    const real = this.workflows();
    const pending = this.pendingWorkflows.pending();
    const isResolved = pending
      ? real.some((workflow) => workflow.clientPromptId === pending.clientPromptId)
      : true;
    const placeholders = pending && !isResolved ? [this.toPlaceholderWorkflow(pending)] : [];
    return [...placeholders, ...real].sort((a, b) =>
      (b.createdAt ?? '').localeCompare(a.createdAt ?? '')
    );
  });

  private expandedIds = signal<ReadonlySet<string>>(new Set());
  private autoExpandedPromptIds = new Set<string>();

  constructor() {
    effect(() => {
      const pending = this.pendingWorkflows.pending();
      if (!pending) {
        return;
      }
      const isResolved = this.workflows().some(
        (workflow) => workflow.clientPromptId === pending.clientPromptId
      );
      if (isResolved) {
        this.pendingWorkflows.resolve(pending.clientPromptId);
      }
    });

    effect(() => {
      const promptId = this.justSubmittedPromptId();
      if (!promptId || this.autoExpandedPromptIds.has(promptId)) {
        return;
      }
      this.autoExpandedPromptIds.add(promptId);
      this.expand(promptId);
    });
  }

  private toPlaceholderWorkflow(entry: PendingWorkflow): Workflow {
    return {
      id: `${PENDING_ID_PREFIX}${entry.clientPromptId}`,
      clientPromptId: entry.clientPromptId,
      createdAt: new Date(entry.submittedAt).toISOString(),
      status: 'RUNNING'
    };
  }

  isExpanded(clientPromptId: string): boolean {
    return this.expandedIds().has(clientPromptId);
  }

  toggle(clientPromptId: string): void {
    if (this.isExpanded(clientPromptId)) {
      this.collapse(clientPromptId);
    } else {
      this.expand(clientPromptId);
    }
  }

  private expand(clientPromptId: string): void {
    const next = new Set(this.expandedIds());
    next.add(clientPromptId);
    this.expandedIds.set(next);
  }

  private collapse(clientPromptId: string): void {
    const next = new Set(this.expandedIds());
    next.delete(clientPromptId);
    this.expandedIds.set(next);
  }
}
