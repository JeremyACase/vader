import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { ActiveWorkflowsService } from '../active-workflows.service';
import { Task, Workflow } from '../client-prompt.model';
import { DaoPage } from '../dao-page.model';
import { TaskAttempt, TaskAttemptTranscript } from '../task-attempt.model';
import { TaskUpdate } from '../task-update.model';
import { WorkflowDagComponent } from './workflow-dag.component';

class FakeActiveWorkflowsService {
  attemptsByTaskId = new Map<string, TaskAttempt[]>();

  recentWorkflows(): Observable<DaoPage<Workflow>> {
    return of({ content: [], totalElements: 0 });
  }

  workflow(): Observable<Workflow> {
    return of({ id: 'wf-1', clientPromptId: 'prompt-1', status: 'RUNNING' });
  }

  taskAttempts(taskId: string): Observable<TaskAttempt[]> {
    return of(this.attemptsByTaskId.get(taskId) ?? []);
  }

  transcripts(): Observable<TaskAttemptTranscript[]> {
    return of([]);
  }

  taskUpdates(): Observable<TaskUpdate[]> {
    return of([]);
  }

  promptText(): Observable<string> {
    return of('');
  }
}

function task(overrides: Partial<Task> & Pick<Task, 'id' | 'title'>): Task {
  return { description: 'description', subTasks: [], dependsOnTaskIds: [], ...overrides };
}

function workflow(tasks: Task[]): Workflow {
  return {
    id: 'wf-1',
    clientPromptId: 'prompt-1',
    status: 'RUNNING',
    taskPlan: { objective: 'ship it', taskGraph: { tasks } }
  };
}

function attempt(overrides: Partial<TaskAttempt> & Pick<TaskAttempt, 'taskId'>): TaskAttempt {
  return { attemptNumber: 1, status: 'RUNNING', turnsUsed: 0, tokensUsed: 0, ...overrides };
}

describe('WorkflowDagComponent', () => {
  let fixture: ComponentFixture<WorkflowDagComponent>;
  let fakeService: FakeActiveWorkflowsService;

  beforeEach(() => {
    fakeService = new FakeActiveWorkflowsService();
    TestBed.configureTestingModule({
      imports: [WorkflowDagComponent],
      providers: [{ provide: ActiveWorkflowsService, useValue: fakeService }]
    });
    fixture = TestBed.createComponent(WorkflowDagComponent);
  });

  it('shows a placeholder when the task plan is not ready yet', () => {
    fixture.componentRef.setInput('workflow', workflow([]));
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain("isn't ready yet");
  });

  it('derives gray/inactive for a task with no attempts', () => {
    fixture.componentRef.setInput('workflow', workflow([task({ id: 't1', title: 'Design' })]));
    fixture.detectChanges();

    expect(fixture.componentInstance.statusOf('t1')).toBe('inactive');
  });

  it('derives green/succeeded from the latest attempt', () => {
    fakeService.attemptsByTaskId.set('t1', [attempt({ taskId: 't1', status: 'SUCCEEDED' })]);
    fixture.componentRef.setInput('workflow', workflow([task({ id: 't1', title: 'Design' })]));
    fixture.detectChanges();

    expect(fixture.componentInstance.statusOf('t1')).toBe('succeeded');
  });

  it('emits the task id and marks it selected when a node is clicked', () => {
    fixture.componentRef.setInput('workflow', workflow([task({ id: 't1', title: 'Design' })]));
    fixture.detectChanges();
    let emitted: string | undefined;
    fixture.componentInstance.taskSelected.subscribe((id) => (emitted = id));

    fixture.componentInstance.select('t1');

    expect(emitted).toBe('t1');
    expect(fixture.componentInstance.isSelected('t1')).toBeTrue();
  });

  it('flattens subtasks into the graph', () => {
    const child = task({ id: 'child', title: 'Child' });
    const root = task({ id: 'root', title: 'Root', subTasks: [child] });
    fixture.componentRef.setInput('workflow', workflow([root]));
    fixture.detectChanges();

    expect(fixture.componentInstance.tasks().map((t) => t.id)).toEqual(['root', 'child']);
  });
});
