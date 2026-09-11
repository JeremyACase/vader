import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Subscription } from 'rxjs';
import { Workflow } from '../client-prompt.model';
import { DaoPage } from '../dao-page.model';
import { LongPollWorkflowUpdatesStrategy } from './long-poll-workflow-updates.strategy';

const POLL_INTERVAL_MS = 3000;

function workflowPage(content: Workflow[]): DaoPage<Workflow> {
  return { content };
}

describe('LongPollWorkflowUpdatesStrategy', () => {
  let strategy: LongPollWorkflowUpdatesStrategy;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    strategy = TestBed.inject(LongPollWorkflowUpdatesStrategy);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('queries recent workflows sorted by creation time, regardless of status', fakeAsync(() => {
    const sub = strategy.recentWorkflows().subscribe();
    tick(0);

    const req = httpMock.expectOne(
      (r) => r.url === '/vader/core-server/data/workflow/query/params'
    );
    expect(req.request.params.has('status')).toBeFalse();
    expect(req.request.params.get('sort-by-fields')).toBe('createdAt');
    expect(req.request.params.get('sort-descending')).toBe('true');
    req.flush(workflowPage([]));

    sub.unsubscribe();
  }));

  it('re-polls on the configured interval while subscribed', fakeAsync(() => {
    const matchWorkflowRequests = () =>
      httpMock.match((r) => r.url === '/vader/core-server/data/workflow/query/params');

    const sub = strategy.recentWorkflows().subscribe();
    tick(0);
    matchWorkflowRequests()[0].flush(workflowPage([]));

    tick(POLL_INTERVAL_MS);
    const secondPoll = matchWorkflowRequests();
    expect(secondPoll.length).toBe(1);
    secondPoll[0].flush(workflowPage([]));

    sub.unsubscribe();
  }));

  it('shares one poll across multiple subscribers to the same task', fakeAsync(() => {
    const subA: Subscription = strategy.taskAttempts('task-1').subscribe();
    const subB: Subscription = strategy.taskAttempts('task-1').subscribe();
    tick(0);

    const requests = httpMock.match(
      (r) => r.url === '/vader/core-server/data/task-attempt/query/params'
    );
    expect(requests.length).toBe(1);
    requests[0].flush({ content: [] });

    subA.unsubscribe();
    subB.unsubscribe();
  }));

  it('stops polling once every subscriber unsubscribes', fakeAsync(() => {
    const sub = strategy.taskAttempts('task-2').subscribe();
    tick(0);
    httpMock
      .expectOne((r) => r.url === '/vader/core-server/data/task-attempt/query/params')
      .flush({ content: [] });

    sub.unsubscribe();
    tick(POLL_INTERVAL_MS * 2);

    const requestsAfterUnsubscribe = httpMock.match(
      (r) => r.url === '/vader/core-server/data/task-attempt/query/params'
    );
    expect(requestsAfterUnsubscribe.length).toBe(0);
  }));

  it('fetches prompt text once and caches it for later subscribers', fakeAsync(() => {
    const subA = strategy.promptText('prompt-1').subscribe();
    httpMock
      .expectOne('/vader/core-server/data/client-prompt/query/prompt-1')
      .flush({ modelType: 'ClientPrompt', text: 'do the thing' });
    subA.unsubscribe();

    let secondValue: string | undefined;
    const subB = strategy.promptText('prompt-1').subscribe((value) => (secondValue = value));
    httpMock.expectNone('/vader/core-server/data/client-prompt/query/prompt-1');
    expect(secondValue).toBe('do the thing');
    subB.unsubscribe();
  }));
});
