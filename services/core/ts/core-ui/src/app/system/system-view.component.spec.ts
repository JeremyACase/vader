import { ComponentFixture, TestBed, discardPeriodicTasks, fakeAsync, tick } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { QueueMessagePage, QueueSummary } from './queues/queue.model';
import { QueueService } from './queues/queue.service';
import { SystemViewComponent } from './system-view.component';

class FakeQueueService {
  queues(): Observable<QueueSummary[]> {
    return of([]);
  }

  messages(): Observable<QueueMessagePage> {
    return of({ content: [], page: 0, size: 25, totalElements: 0, totalPages: 0 });
  }
}

describe('SystemViewComponent', () => {
  let fixture: ComponentFixture<SystemViewComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [SystemViewComponent],
      providers: [{ provide: QueueService, useValue: new FakeQueueService() }]
    });
    fixture = TestBed.createComponent(SystemViewComponent);
  });

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  it('offers the Queues tool and opens nothing until one is chosen', () => {
    fixture.detectChanges();

    const tools = Array.from(element().querySelectorAll('.nav-item'), (el) => el.getAttribute('aria-label'));
    expect(tools).toEqual(['Queues']);
    expect(element().querySelector('app-queues-view')).toBeNull();
  });

  it('opens the Queues view when its icon is clicked, and closes it on a second click', fakeAsync(() => {
    fixture.detectChanges();
    const queuesIcon = element().querySelector('.nav-item') as HTMLButtonElement;

    queuesIcon.click();
    fixture.detectChanges();
    tick();
    expect(element().querySelector('app-queues-view')).not.toBeNull();

    queuesIcon.click();
    fixture.detectChanges();
    expect(element().querySelector('app-queues-view')).toBeNull();
    discardPeriodicTasks();
  }));
});
