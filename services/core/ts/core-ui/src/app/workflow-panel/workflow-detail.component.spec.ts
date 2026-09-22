import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, Subject, of } from 'rxjs';
import { ActiveWorkflowsService } from '../active-workflows.service';
import { Workflow } from '../client-prompt.model';
import { DaoPage } from '../dao-page.model';
import { TaskAttempt, TaskAttemptTranscript } from '../task-attempt.model';
import { TaskUpdate } from '../task-update.model';
import { WorkflowDetailComponent } from './workflow-detail.component';

class FakeActiveWorkflowsService {
  recentWorkflows(): Observable<DaoPage<Workflow>> {
    return new Subject<DaoPage<Workflow>>().asObservable();
  }

  workflow(): Observable<Workflow> {
    return new Subject<Workflow>().asObservable();
  }

  taskAttempts(): Observable<TaskAttempt[]> {
    return of([]);
  }

  transcripts(): Observable<TaskAttemptTranscript[]> {
    return new Subject<TaskAttemptTranscript[]>().asObservable();
  }

  taskUpdates(): Observable<TaskUpdate[]> {
    return of([]);
  }

  promptText(): Observable<string> {
    return of('What does this spreadsheet show?');
  }
}

function workflow(overrides: Partial<Workflow>): Workflow {
  return {
    id: 'wf-1',
    clientPromptId: 'prompt-1',
    status: 'SUCCEEDED',
    ...overrides
  };
}

describe('WorkflowDetailComponent', () => {
  let fixture: ComponentFixture<WorkflowDetailComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [WorkflowDetailComponent],
      providers: [{ provide: ActiveWorkflowsService, useClass: FakeActiveWorkflowsService }]
    });
    fixture = TestBed.createComponent(WorkflowDetailComponent);
  });

  it('shows the final result when the workflow has one', () => {
    fixture.componentRef.setInput(
      'workflow',
      workflow({ result: 'The spreadsheet tracks quarterly sales by region.' })
    );
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Result');
    expect(text).toContain('The spreadsheet tracks quarterly sales by region.');
  });

  it('omits the result section while the workflow has no result yet', () => {
    fixture.componentRef.setInput('workflow', workflow({ result: undefined }));
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).not.toContain('Result');
  });

  it('shows the task detail once a task node is selected', () => {
    fixture.componentRef.setInput(
      'workflow',
      workflow({
        taskPlan: {
          objective: 'Ship it',
          taskGraph: {
            tasks: [
              { id: 't1', title: 'Design', description: 'Draw it', subTasks: [], dependsOnTaskIds: [] }
            ]
          }
        }
      })
    );
    fixture.detectChanges();

    expect(fixture.componentInstance.selectedTask()).toBeUndefined();
    fixture.componentInstance.selectTask('t1');
    fixture.detectChanges();

    expect(fixture.componentInstance.selectedTask()?.title).toBe('Design');
  });
});
