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
  updatesByTaskId = new Map<string, TaskUpdate[]>();

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

  taskUpdates(taskId: string): Observable<TaskUpdate[]> {
    return of(this.updatesByTaskId.get(taskId) ?? []);
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
  return {
    id: `${overrides.taskId}-a1`,
    attemptNumber: 1,
    status: 'RUNNING',
    turnsUsed: 0,
    tokensUsed: 0,
    ...overrides
  };
}

function verdict(taskId: string, type: TaskUpdate['type']): TaskUpdate {
  return { taskId, taskAttemptId: `${taskId}-a1`, type, description: 'why', author: 'EVALUATOR' };
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

  it("derives green/succeeded from the evaluator's verdict", () => {
    fakeService.attemptsByTaskId.set('t1', [attempt({ taskId: 't1', status: 'SUCCEEDED' })]);
    fakeService.updatesByTaskId.set('t1', [verdict('t1', 'COMPLETED')]);
    fixture.componentRef.setInput('workflow', workflow([task({ id: 't1', title: 'Design' })]));
    fixture.detectChanges();

    expect(fixture.componentInstance.statusOf('t1')).toBe('succeeded');
  });

  it('derives red/failed when the evaluator rejects a self-reported success', () => {
    fakeService.attemptsByTaskId.set('t1', [attempt({ taskId: 't1', status: 'SUCCEEDED' })]);
    fakeService.updatesByTaskId.set('t1', [verdict('t1', 'FAILED')]);
    fixture.componentRef.setInput('workflow', workflow([task({ id: 't1', title: 'Design' })]));
    fixture.detectChanges();

    expect(fixture.componentInstance.statusOf('t1')).toBe('failed');
  });

  it('keeps a decomposed parent blue until its runtime subtasks settle', () => {
    const child = task({ id: 'c1', title: 'Run the fix', spawnedByAttemptId: 'p-a1' });
    const parent = task({ id: 'p', title: 'Clean', subTasks: [child] });
    fakeService.attemptsByTaskId.set('p', [attempt({ taskId: 'p', status: 'SUCCEEDED' })]);
    fakeService.updatesByTaskId.set('p', [verdict('p', 'DECOMPOSED')]);
    fakeService.attemptsByTaskId.set('c1', [attempt({ taskId: 'c1', status: 'RUNNING' })]);
    fixture.componentRef.setInput('workflow', workflow([parent]));
    fixture.detectChanges();

    expect(fixture.componentInstance.statusOf('p')).toBe('active');
    expect(fixture.componentInstance.statusOf('c1')).toBe('active');
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
