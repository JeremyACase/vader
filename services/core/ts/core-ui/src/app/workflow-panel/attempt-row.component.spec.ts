import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, Subject } from 'rxjs';
import { ActiveWorkflowsService } from '../active-workflows.service';
import { Workflow } from '../client-prompt.model';
import { DaoPage } from '../dao-page.model';
import { TaskAttempt, TaskAttemptTranscript } from '../task-attempt.model';
import { TaskUpdate } from '../task-update.model';
import { AttemptRowComponent } from './attempt-row.component';

class FakeActiveWorkflowsService {
  readonly transcriptsSubject = new Subject<TaskAttemptTranscript[]>();

  recentWorkflows(): Observable<DaoPage<Workflow>> {
    return new Subject<DaoPage<Workflow>>().asObservable();
  }

  workflow(): Observable<Workflow> {
    return new Subject<Workflow>().asObservable();
  }

  taskAttempts(): Observable<TaskAttempt[]> {
    return new Subject<TaskAttempt[]>().asObservable();
  }

  transcripts(): Observable<TaskAttemptTranscript[]> {
    return this.transcriptsSubject.asObservable();
  }

  taskUpdates(): Observable<TaskUpdate[]> {
    return new Subject<TaskUpdate[]>().asObservable();
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

function transcript(overrides: Partial<TaskAttemptTranscript>): TaskAttemptTranscript {
  return {
    id: 'turn-1',
    taskAttemptId: 'attempt-1',
    turnIndex: 0,
    prompt: '[]',
    response: '',
    tokensSpent: 10,
    ...overrides
  };
}

describe('AttemptRowComponent', () => {
  let fixture: ComponentFixture<AttemptRowComponent>;
  let service: FakeActiveWorkflowsService;

  beforeEach(() => {
    service = new FakeActiveWorkflowsService();
    TestBed.configureTestingModule({
      imports: [AttemptRowComponent],
      providers: [{ provide: ActiveWorkflowsService, useValue: service }]
    });
    fixture = TestBed.createComponent(AttemptRowComponent);
  });

  function renderTurns(turns: TaskAttemptTranscript[]): HTMLElement {
    fixture.componentRef.setInput('attempt', attempt({}));
    fixture.detectChanges();
    service.transcriptsSubject.next(turns);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('flags a turn cut off at the output token cap', () => {
    const element = renderTurns([transcript({ finishReason: 'length' })]);

    const finish = element.querySelector('.turn-finish');
    expect(finish?.textContent).toContain('cut off at the output token cap');
    expect(finish?.classList).toContain('cut-off');
  });

  it('shows an ordinary finish reason without flagging it', () => {
    const element = renderTurns([transcript({ finishReason: 'stop' })]);

    const finish = element.querySelector('.turn-finish');
    expect(finish?.textContent).toContain('finished: stop');
    expect(finish?.classList).not.toContain('cut-off');
  });

  it('omits the finish reason for turns recorded without one', () => {
    const element = renderTurns([transcript({ finishReason: null })]);

    expect(element.querySelector('.turn-finish')).toBeNull();
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
