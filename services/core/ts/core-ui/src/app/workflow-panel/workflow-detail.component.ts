import { Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { switchMap } from 'rxjs/operators';
import { ActiveWorkflowsService } from '../active-workflows.service';
import { Task, Workflow } from '../client-prompt.model';
import { TaskRowComponent } from './task-row.component';

/** Renders one expanded workflow: its originating prompt, task plan, and task graph. */
@Component({
  selector: 'app-workflow-detail',
  standalone: true,
  imports: [TaskRowComponent],
  templateUrl: './workflow-detail.component.html',
  styleUrl: './workflow-detail.component.css'
})
export class WorkflowDetailComponent {
  private activeWorkflowsService = inject(ActiveWorkflowsService);

  workflow = input.required<Workflow>();

  private clientPromptId = computed(() => this.workflow().clientPromptId);

  promptText = toSignal(
    toObservable(this.clientPromptId).pipe(
      switchMap((id) => this.activeWorkflowsService.promptText(id))
    ),
    { initialValue: '' }
  );

  readonly tasks = computed<Task[]>(() => this.workflow().taskPlan?.taskGraph.tasks ?? []);

  private expandedTaskIds = signal<ReadonlySet<string>>(new Set());

  isExpanded(taskId: string): boolean {
    return this.expandedTaskIds().has(taskId);
  }

  toggleTask(taskId: string): void {
    const next = new Set(this.expandedTaskIds());
    if (next.has(taskId)) {
      next.delete(taskId);
    } else {
      next.add(taskId);
    }
    this.expandedTaskIds.set(next);
  }
}
