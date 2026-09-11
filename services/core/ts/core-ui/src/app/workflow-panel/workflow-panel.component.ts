import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActiveWorkflowsService } from '../active-workflows.service';
import { Workflow } from '../client-prompt.model';
import { WorkflowDetailComponent } from './workflow-detail.component';

/**
 * Persistent panel of the most recent workflows, independent of prompt submission — a new
 * prompt can be submitted while others are still running. Shows every recent workflow regardless
 * of status, not just `RUNNING` ones, so a workflow that just failed or succeeded stays visible
 * instead of silently disappearing. Auto-expands the workflow spawned from
 * `justSubmittedPromptId` the first time it appears in the list.
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

  justSubmittedPromptId = input<string | null>(null);

  private workflows = toSignal(this.activeWorkflowsService.recentWorkflows(), {
    initialValue: [] as Workflow[]
  });

  readonly sortedWorkflows = computed(() =>
    [...this.workflows()].sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''))
  );

  private expandedIds = signal<ReadonlySet<string>>(new Set());
  private autoExpandedPromptIds = new Set<string>();

  constructor() {
    effect(() => {
      const promptId = this.justSubmittedPromptId();
      if (!promptId || this.autoExpandedPromptIds.has(promptId)) {
        return;
      }
      const match = this.workflows().find((workflow) => workflow.clientPromptId === promptId);
      if (match?.id) {
        this.autoExpandedPromptIds.add(promptId);
        this.expand(match.id);
      }
    });
  }

  isExpanded(workflowId: string): boolean {
    return this.expandedIds().has(workflowId);
  }

  toggle(workflowId: string): void {
    if (this.isExpanded(workflowId)) {
      this.collapse(workflowId);
    } else {
      this.expand(workflowId);
    }
  }

  private expand(workflowId: string): void {
    const next = new Set(this.expandedIds());
    next.add(workflowId);
    this.expandedIds.set(next);
  }

  private collapse(workflowId: string): void {
    const next = new Set(this.expandedIds());
    next.delete(workflowId);
    this.expandedIds.set(next);
  }
}
