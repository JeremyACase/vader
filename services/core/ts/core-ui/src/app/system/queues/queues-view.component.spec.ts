import { ComponentFixture, TestBed, discardPeriodicTasks, fakeAsync, tick } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { TaskAttempt } from '../../task-attempt.model';
import {
  OutboxMessageStatus,
  QueueMessage,
  QueueMessagePage,
  QueueMessageSummary,
  QueueSummary
} from './queue.model';
import { QueueService } from './queue.service';
import { QUEUE_POLL_INTERVAL_MS, QueuesViewComponent } from './queues-view.component';

function summary(name: string, counts: Partial<Record<OutboxMessageStatus, number>> = {}): QueueSummary {
  return {
    name,
    maxOpenMessages: 1,
    statusCounts: { PENDING: 0, CLAIMED: 0, PROCESSED: 0, FAILED: 0, ...counts }
  };
}

function row(id: string, status: OutboxMessageStatus): QueueMessageSummary {
  return { id, modelType: 'LlmRequestOutboxMessage', status, createdAt: '2026-01-01T00:00:00Z', attempts: 1, subject: 'EVALUATION' };
}

class FakeQueueService {
  summaries: QueueSummary[] = [];
  rows: QueueMessageSummary[] = [];
  requests: { queue: string; page: number; status: OutboxMessageStatus | null }[] = [];

  queues(): Observable<QueueSummary[]> {
    return of(this.summaries);
  }

  messages(queue: string, page: number, size: number, status: OutboxMessageStatus | null): Observable<QueueMessagePage> {
    this.requests.push({ queue, page, status });
    return of({ content: this.rows, page, size, totalElements: this.rows.length, totalPages: 1 });
  }

  message(): Observable<QueueMessage | null> {
    return of(null);
  }

  taskAttempt(): Observable<TaskAttempt | null> {
    return of(null);
  }
}

describe('QueuesViewComponent', () => {
  let fixture: ComponentFixture<QueuesViewComponent>;
  let component: QueuesViewComponent;
  let fakeService: FakeQueueService;

  beforeEach(() => {
    fakeService = new FakeQueueService();
    fakeService.summaries = [
      summary('LlmRequest', { PENDING: 3, CLAIMED: 1, PROCESSED: 10 }),
      summary('ClientPrompt', { PROCESSED: 2, FAILED: 1 })
    ];
    fakeService.rows = [row('m-1', 'PROCESSED'), row('m-2', 'PENDING')];
    TestBed.configureTestingModule({
      imports: [QueuesViewComponent],
      providers: [{ provide: QueueService, useValue: fakeService }]
    });
  });

  /** Creates the component inside the test's fakeAsync zone, so its poll timers can be ticked. */
  function create(): void {
    fixture = TestBed.createComponent(QueuesViewComponent);
    component = fixture.componentInstance;
  }

  function render(): void {
    tick();
    fixture.detectChanges();
    tick();
    fixture.detectChanges();
  }

  it('renders a card per queue in pipeline order and opens the first', fakeAsync(() => {
    create();
    render();

    const names = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('.queue-name'),
      (el) => el.textContent?.trim()
    );
    expect(names).toEqual(['Client prompts', 'LLM requests']);
    expect(component.activeQueueName()).toBe('ClientPrompt');
    discardPeriodicTasks();
  }));

  it('lists the chosen queue’s messages', fakeAsync(() => {
    create();
    render();

    component.selectQueue('LlmRequest');
    render();

    const rows = (fixture.nativeElement as HTMLElement).querySelectorAll('.row');
    expect(rows.length).toBe(2);
    expect(fakeService.requests.at(-1)).toEqual({ queue: 'LlmRequest', page: 0, status: null });
    discardPeriodicTasks();
  }));

  it('filters by status, and choosing the same status again clears the filter', fakeAsync(() => {
    create();
    render();

    component.toggleStatus('FAILED');
    render();
    expect(fakeService.requests.at(-1)?.status).toBe('FAILED');

    component.toggleStatus('FAILED');
    render();
    expect(fakeService.requests.at(-1)?.status).toBeNull();
    discardPeriodicTasks();
  }));

  it('counts the active queue’s messages per status', fakeAsync(() => {
    create();
    render();
    component.selectQueue('LlmRequest');
    render();

    expect(component.count('PENDING')).toBe(3);
    expect(component.count(null)).toBe(14);
    discardPeriodicTasks();
  }));

  it('clears the selected message when switching queues', fakeAsync(() => {
    create();
    render();
    component.selectMessage('m-1');
    expect(component.isSelected('m-1')).toBeTrue();

    component.selectQueue('LlmRequest');

    expect(component.selectedMessageId()).toBeNull();
    discardPeriodicTasks();
  }));

  it('keeps polling the queues', fakeAsync(() => {
    const queues = spyOn(fakeService, 'queues').and.callThrough();
    create();
    render();

    tick(QUEUE_POLL_INTERVAL_MS);

    expect(queues).toHaveBeenCalledTimes(2);
    discardPeriodicTasks();
  }));
});
