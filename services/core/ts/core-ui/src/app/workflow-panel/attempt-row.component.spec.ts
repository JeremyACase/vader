import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, Subject } from 'rxjs';
import { ActiveWorkflowsService } from '../active-workflows.service';
import { Workflow } from '../client-prompt.model';
import { TaskAttempt, TaskAttemptTranscript } from '../task-attempt.model';
import { AttemptRowComponent } from './attempt-row.component';

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
    return new Subject<string>().asObservable();
  }
}

function attempt(overrides: Partial<TaskAttempt>): TaskAttempt {
  return {
    id: 'attempt-1',
    taskId: 'task-1',
    attemptNumber: 1,
    status: 'SUCCEEDED',
    turnsUsed: 1,
    tokensUsed: 10,
    ...overrides
  };
}

describe('AttemptRowComponent', () => {
  let fixture: ComponentFixture<AttemptRowComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AttemptRowComponent],
      providers: [{ provide: ActiveWorkflowsService, useClass: FakeActiveWorkflowsService }]
    });
    fixture = TestBed.createComponent(AttemptRowComponent);
  });

  it('shows the result when the attempt reported one', () => {
    fixture.componentRef.setInput('attempt', attempt({ result: 'The answer is 42.' }));
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Result:');
    expect(text).toContain('The answer is 42.');
  });

  it('omits the result section when the attempt has no result', () => {
    fixture.componentRef.setInput('attempt', attempt({ result: undefined }));
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).not.toContain('Result:');
  });
});
