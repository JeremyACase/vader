import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of, Subject } from 'rxjs';
import { ActiveWorkflowsService } from '../active-workflows.service';
import { Workflow } from '../client-prompt.model';
import { TaskAttempt, TaskAttemptTranscript } from '../task-attempt.model';
import { WorkflowDetailComponent } from './workflow-detail.component';

class FakeActiveWorkflowsService {
  recentWorkflows(): Observable<Workflow[]> {
    return new Subject<Workflow[]>().asObservable();
  }

  taskAttempts(): Observable<TaskAttempt[]> {
    return new Subject<TaskAttempt[]>().asObservable();
  }

  transcripts(): Observable<TaskAttemptTranscript[]> {
    return new Subject<TaskAttemptTranscript[]>().asObservable();
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
});
