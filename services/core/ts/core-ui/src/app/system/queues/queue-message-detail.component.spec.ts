import { ComponentFixture, TestBed, discardPeriodicTasks, fakeAsync, tick } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { ActiveWorkflowsService } from '../../active-workflows.service';
import { TaskAttempt } from '../../task-attempt.model';
import { MESSAGE_POLL_INTERVAL_MS, QueueMessageDetailComponent } from './queue-message-detail.component';
import { QueueMessage } from './queue.model';
import { QueueService } from './queue.service';

class FakeQueueService {
  current: QueueMessage | null = null;
  attempt: TaskAttempt | null = null;
  fetches = 0;

  message(): Observable<QueueMessage | null> {
    this.fetches++;
    return of(this.current);
  }

  taskAttempt(): Observable<TaskAttempt | null> {
    return of(this.attempt);
  }
}

class FakeActiveWorkflowsService {
  promptText(): Observable<string> {
    return of('Plan a birthday party.');
  }
}

describe('QueueMessageDetailComponent', () => {
  let fixture: ComponentFixture<QueueMessageDetailComponent>;
  let fakeService: FakeQueueService;

  beforeEach(() => {
    fakeService = new FakeQueueService();
    TestBed.configureTestingModule({
      imports: [QueueMessageDetailComponent],
      providers: [
        { provide: QueueService, useValue: fakeService },
        { provide: ActiveWorkflowsService, useValue: new FakeActiveWorkflowsService() }
      ]
    });
    fixture = TestBed.createComponent(QueueMessageDetailComponent);
  });

  function show(queue: string, message: QueueMessage | null): void {
    fakeService.current = message;
    fixture.componentRef.setInput('queue', queue);
    fixture.componentRef.setInput('messageId', message?.id ?? 'gone');
    fixture.detectChanges();
    tick();
    fixture.detectChanges();
  }

  function text(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  it('shows a client-prompt message with its prompt text', fakeAsync(() => {
    show('ClientPrompt', {
      modelType: 'ClientPromptOutboxMessage',
      id: 'm-1',
      createdAt: '2026-01-01T00:00:00Z',
      status: 'PROCESSED',
      attempts: 1,
      clientPromptId: 'prompt-1'
    });

    expect(text()).toContain('Processed successfully');
    expect(text()).toContain('Plan a birthday party.');
  }));

  it('shows an LLM request and its response, pretty-printed', fakeAsync(() => {
    show('LlmRequest', {
      modelType: 'LlmRequestOutboxMessage',
      id: 'm-2',
      createdAt: '2026-01-01T00:00:00Z',
      status: 'PROCESSED',
      attempts: 1,
      kind: 'EVALUATION',
      requestJson: '{"attempt":"a-1"}',
      responseJson: '{"value":{"verdict":"COMPLETED"}}'
    });

    const blocks = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('pre.json'),
      (el) => el.textContent
    );
    expect(blocks).toEqual(['{\n  "attempt": "a-1"\n}', '{\n  "value": {\n    "verdict": "COMPLETED"\n  }\n}']);
    expect(text()).toContain('EVALUATION');
  }));

  it('shows why a message failed', fakeAsync(() => {
    show('TaskAssignment', {
      modelType: 'TaskAssignmentOutboxMessage',
      id: 'm-3',
      createdAt: '2026-01-01T00:00:00Z',
      status: 'FAILED',
      attempts: 1,
      failureReason: 'KubernetesClientException: quota exceeded',
      taskAttemptId: 'attempt-1'
    });

    expect(text()).toContain('quota exceeded');
  }));

  it('says so when the message no longer exists', fakeAsync(() => {
    show('ClientPrompt', null);

    expect(text()).toContain('no longer exists');
  }));

  it('keeps polling an open message and stops once it settles', fakeAsync(() => {
    const open: QueueMessage = {
      modelType: 'ClientPromptOutboxMessage',
      id: 'm-4',
      createdAt: '2026-01-01T00:00:00Z',
      status: 'CLAIMED',
      attempts: 1,
      clientPromptId: 'prompt-1'
    };
    show('ClientPrompt', open);
    expect(fakeService.fetches).toBe(1);

    fakeService.current = { ...open, status: 'PROCESSED', processedAt: '2026-01-01T00:00:05Z' };
    tick(MESSAGE_POLL_INTERVAL_MS);
    tick(MESSAGE_POLL_INTERVAL_MS);

    expect(fakeService.fetches).toBe(2);
    discardPeriodicTasks();
  }));
});
