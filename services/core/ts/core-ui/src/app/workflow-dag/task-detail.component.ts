import { Component, input } from '@angular/core';
import { Task } from '../client-prompt.model';
import { TaskRowComponent } from '../workflow-panel/task-row.component';
import { TaskUpdateHistoryComponent } from './task-update-history.component';

/** Renders the selected DAG node's full detail: its description, attempt history (each
 *  expandable to its own chain-of-thought), and its update history. */
@Component({
  selector: 'app-task-detail',
  standalone: true,
  imports: [TaskRowComponent, TaskUpdateHistoryComponent],
  templateUrl: './task-detail.component.html',
  styleUrl: './task-detail.component.css'
})
export class TaskDetailComponent {
  task = input.required<Task>();
}
