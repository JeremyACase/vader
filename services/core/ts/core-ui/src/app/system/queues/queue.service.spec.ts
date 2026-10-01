import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { QueueMessage, QueueMessagePage, QueueSummary } from './queue.model';
import { QueueService } from './queue.service';

describe('QueueService', () => {
  let service: QueueService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(QueueService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('fetches every queue summary', () => {
    let result: QueueSummary[] | undefined;
    service.queues().subscribe((queues) => (result = queues));

    http.expectOne('/vader/core-server/queue').flush([]);

    expect(result).toEqual([]);
  });

  it('pages a queue without a status filter', () => {
    service.messages('LlmRequest', 2, 25, null).subscribe();

    const request = http.expectOne((req) => req.url === '/vader/core-server/queue/LlmRequest/message');
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('25');
    expect(request.request.params.has('status')).toBeFalse();
    request.flush({ content: [], page: 2, size: 25, totalElements: 0, totalPages: 0 } satisfies QueueMessagePage);
  });

  it('passes the status filter through', () => {
    service.messages('ClientPrompt', 0, 25, 'FAILED').subscribe();

    const request = http.expectOne((req) => req.url === '/vader/core-server/queue/ClientPrompt/message');
    expect(request.request.params.get('status')).toBe('FAILED');
    request.flush({ content: [], page: 0, size: 25, totalElements: 0, totalPages: 0 });
  });

  it('resolves a message that no longer exists (204) to null', () => {
    let result: QueueMessage | null | undefined;
    service.message('ClientPrompt', 'gone').subscribe((message) => (result = message));

    http.expectOne('/vader/core-server/queue/ClientPrompt/message/gone').flush(null, {
      status: 204,
      statusText: 'No Content'
    });

    expect(result).toBeNull();
  });
});
