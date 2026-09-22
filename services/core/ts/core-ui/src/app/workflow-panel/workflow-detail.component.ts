import { Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { switchMap } from 'rxjs/operators';
import { ActiveWorkflowsService } from '../active-workflows.service';
import { Task, Workflow } from '../client-prompt.model';
import { flattenTasks } from '../workflow-dag/task-graph-layout.builder';
import { TaskDetailComponent } from '../workflow-dag/task-detail.component';
import { WorkflowDagComponent } from '../workflow-dag/workflow-dag.component';

/** Renders one selected workflow: its originating prompt, task graph (as a DAG), and — once a
 *  node is clicked — that task's full detail. */
@Component({
  selector: 'app-workflow-detail',
  standalone: true,
  imports: [WorkflowDagComponent, TaskDetailComponent],
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

  private tasks = computed<Task[]>(() =>
    flattenTasks(this.workflow().taskPlan?.taskGraph.tasks ?? [])
  );

  selectedTaskId = signal<string | null>(null);

  readonly selectedTask = computed<Task | undefined>(() => {
    const id = this.selectedTaskId();
    return id ? this.tasks().find((task) => task.id === id) : undefined;
  });

  selectTask(taskId: string): void {
    this.selectedTaskId.set(taskId);
  }
}
