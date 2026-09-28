import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { switchMap } from 'rxjs/operators';
import { ActiveWorkflowsService } from '../active-workflows.service';
import { Workflow } from '../client-prompt.model';
import { DaoPage } from '../dao-page.model';
import { PendingWorkflow, PendingWorkflowRegistry } from '../workflow-updates/pending-workflow.registry';

const PENDING_ID_PREFIX = 'pending:';
const PAGE_SIZE = 10;
const EMPTY_PAGE: DaoPage<Workflow> = { content: [], totalElements: 0 };

/**
 * Persistent left-hand panel: a paginated list of the most recent workflows, regardless of
 * status, so one that just failed or succeeded stays visible instead of disappearing. Clicking
 * a row selects it (emitting its id) for the visualization area to render; the row spawned from
 * `justSubmittedPromptId` is auto-selected the moment its real Workflow row appears.
 *
 * <p>A prompt just accepted by the server has no Workflow row yet — decomposition and the next
 * poll both take a few seconds. Rather than show nothing during that gap, the in-flight entry in
 * {@link PendingWorkflowRegistry} (the form blocks a second submission until it resolves) is
 * rendered as a "Decomposing…" placeholder row on the first page until its real Workflow shows up
 * in a poll, at which point the placeholder is dropped in favor of the real row.</p>
 */
@Component({
  selector: 'app-workflow-panel',
  standalone: true,
  imports: [],
  templateUrl: './workflow-panel.component.html',
  styleUrl: './workflow-panel.component.css'
})
export class WorkflowPanelComponent {
  private activeWorkflowsService = inject(ActiveWorkflowsService);
  private pendingWorkflows = inject(PendingWorkflowRegistry);

  justSubmittedPromptId = input<string | null>(null);
  workflowSelected = output<string>();

  readonly page = signal(0);
  readonly pageSize = PAGE_SIZE;

  readonly workflowsPage = toSignal(
    toObservable(this.page).pipe(
      switchMap((page) => this.activeWorkflowsService.recentWorkflows(page, this.pageSize))
    ),
    { initialValue: EMPTY_PAGE }
  );

  readonly totalPages = computed(() =>
    Math.max(1, Math.ceil(this.workflowsPage().totalElements / this.pageSize))
  );

  readonly canGoPrevious = computed(() => this.page() > 0);
  readonly canGoNext = computed(() => this.page() + 1 < this.totalPages());

  readonly sortedWorkflows = computed(() => {
    const real = this.workflowsPage().content;
    const pending = this.pendingWorkflows.pending();
    const isResolved = pending
      ? real.some((workflow) => workflow.clientPromptId === pending.clientPromptId)
      : true;
    const showPlaceholder = pending && !isResolved && this.page() === 0;
    return showPlaceholder ? [this.toPlaceholderWorkflow(pending), ...real] : real;
  });

  private selectedId = signal<string | null>(null);
  private autoSelectedPromptIds = new Set<string>();

  constructor() {
    effect(() => {
      const pending = this.pendingWorkflows.pending();
      if (!pending) {
        return;
      }
      const isResolved = this.workflowsPage().content.some(
        (workflow) => workflow.clientPromptId === pending.clientPromptId
      );
      if (isResolved) {
        this.pendingWorkflows.resolve(pending.clientPromptId);
      }
    });

    effect(() => {
      const promptId = this.justSubmittedPromptId();
      if (!promptId || this.autoSelectedPromptIds.has(promptId)) {
        return;
      }
      const resolved = this.workflowsPage().content.find(
        (workflow) => workflow.clientPromptId === promptId
      );
      if (!resolved?.id) {
        return;
      }
      this.autoSelectedPromptIds.add(promptId);
      this.select(resolved);
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

  isPlaceholder(workflow: Workflow): boolean {
    return !!workflow.id?.startsWith(PENDING_ID_PREFIX);
  }

  isSelected(workflowId: string | undefined): boolean {
    return !!workflowId && this.selectedId() === workflowId;
  }

  select(workflow: Workflow): void {
    if (!workflow.id || this.isPlaceholder(workflow)) {
      return;
    }
    this.selectedId.set(workflow.id);
    this.workflowSelected.emit(workflow.id);
  }

  previousPage(): void {
    if (this.canGoPrevious()) {
      this.page.update((page) => page - 1);
    }
  }

  nextPage(): void {
    if (this.canGoNext()) {
      this.page.update((page) => page + 1);
    }
  }
}
