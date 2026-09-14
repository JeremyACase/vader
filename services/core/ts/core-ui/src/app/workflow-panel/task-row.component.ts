import { DatePipe } from '@angular/common';
import { Component, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { switchMap } from 'rxjs/operators';
import { ActiveWorkflowsService } from '../active-workflows.service';
import { TaskAttempt } from '../task-attempt.model';
import { AttemptRowComponent } from './attempt-row.component';

/** Renders one task's attempt history, each attempt expandable to show its chain-of-thought. */
@Component({
  selector: 'app-task-row',
  standalone: true,
  imports: [DatePipe, AttemptRowComponent],
  templateUrl: './task-row.component.html',
  styleUrl: './task-row.component.css'
})
export class TaskRowComponent {
  private activeWorkflowsService = inject(ActiveWorkflowsService);

  taskId = input.required<string>();

  attempts = toSignal(
    toObservable(this.taskId).pipe(
      switchMap((id) => this.activeWorkflowsService.taskAttempts(id))
    ),
    { initialValue: [] as TaskAttempt[] }
  );

  private expandedAttemptIds = signal<ReadonlySet<string>>(new Set());

  isExpanded(attemptId: string): boolean {
    return this.expandedAttemptIds().has(attemptId);
  }

  toggleAttempt(attemptId: string): void {
    const next = new Set(this.expandedAttemptIds());
    if (next.has(attemptId)) {
      next.delete(attemptId);
    } else {
      next.add(attemptId);
    }
    this.expandedAttemptIds.set(next);
  }
}
