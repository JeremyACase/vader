import { DatePipe } from '@angular/common';
import { Component, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { switchMap } from 'rxjs/operators';
import { ActiveWorkflowsService } from '../active-workflows.service';
import { TaskUpdate } from '../task-update.model';

/** Renders a task's update history — evaluator verdicts, orchestrator reasoning, and the task
 *  agent's own interim progress notes — most recent first. */
@Component({
  selector: 'app-task-update-history',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './task-update-history.component.html',
  styleUrl: './task-update-history.component.css'
})
export class TaskUpdateHistoryComponent {
  private activeWorkflowsService = inject(ActiveWorkflowsService);

  taskId = input.required<string>();

  updates = toSignal(
    toObservable(this.taskId).pipe(switchMap((id) => this.activeWorkflowsService.taskUpdates(id))),
    { initialValue: [] as TaskUpdate[] }
  );
}
