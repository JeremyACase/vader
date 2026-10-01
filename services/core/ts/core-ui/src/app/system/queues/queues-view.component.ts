import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Observable, of, timer } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import { QueueMessageDetailComponent } from './queue-message-detail.component';
import {
  STATUS_MEANING,
  inPipelineOrder,
  queueDisplay,
  subjectLabel,
  totalMessages
} from './queue-display';
import {
  OUTBOX_MESSAGE_STATUSES,
  OutboxMessageStatus,
  QueueMessagePage,
  QueueMessageSummary,
  QueueSummary
} from './queue.model';
import { QueueService } from './queue.service';

export const QUEUE_POLL_INTERVAL_MS = 3000;
export const QUEUE_PAGE_SIZE = 25;
const EMPTY_PAGE: QueueMessagePage = {
  content: [],
  page: 0,
  size: QUEUE_PAGE_SIZE,
  totalElements: 0,
  totalPages: 0
};

/** One segment of a queue card's status bar. */
interface StatusSegment {
  status: OutboxMessageStatus;
  count: number;
  percent: number;
}

/**
 * System → Queues: every inbox/outbox queue as a card in pipeline order, showing how many of its
 * messages are waiting, in flight, processed and failed. Choosing a queue lists its messages,
 * newest first and filterable by status; choosing a message opens its full detail.
 *
 * <p>Counts and the message list are polled, so the view tracks the queues live.</p>
 */
@Component({
  selector: 'app-queues-view',
  standalone: true,
  imports: [DatePipe, QueueMessageDetailComponent],
  templateUrl: './queues-view.component.html',
  styleUrl: './queues-view.component.css'
})
export class QueuesViewComponent {
  private queueService = inject(QueueService);

  readonly statuses = OUTBOX_MESSAGE_STATUSES;
  readonly statusMeaning = STATUS_MEANING;
  readonly pageSize = QUEUE_PAGE_SIZE;

  /** `null` until the first poll answers; `'unreachable'` while core-server cannot be reached. */
  private queueSummaries = toSignal(
    timer(0, QUEUE_POLL_INTERVAL_MS).pipe(switchMap(() => this.fetchQueues())),
    { initialValue: null }
  );

  readonly isUnreachable = computed(() => this.queueSummaries() === 'unreachable');
  readonly isLoading = computed(() => this.queueSummaries() === null);
  readonly queues = computed(() => {
    const summaries = this.queueSummaries();
    return Array.isArray(summaries) ? inPipelineOrder(summaries) : [];
  });

  private chosenQueue = signal<string | null>(null);
  readonly activeQueueName = computed(() => this.chosenQueue() ?? this.queues()[0]?.name ?? null);
  readonly activeQueue = computed(
    () => this.queues().find((queue) => queue.name === this.activeQueueName()) ?? null
  );

  readonly statusFilter = signal<OutboxMessageStatus | null>(null);
  readonly page = signal(0);
  readonly selectedMessageId = signal<string | null>(null);

  private listQuery = computed(() => ({
    queue: this.activeQueueName(),
    status: this.statusFilter(),
    page: this.page()
  }));

  readonly messagePage = toSignal(
    toObservable(this.listQuery).pipe(
      switchMap(({ queue, status, page }) =>
        queue ? this.pollMessages(queue, status, page) : of(EMPTY_PAGE)
      )
    ),
    { initialValue: EMPTY_PAGE }
  );

  readonly totalPages = computed(() => Math.max(1, this.messagePage().totalPages));
  readonly canGoPrevious = computed(() => this.page() > 0);
  readonly canGoNext = computed(() => this.page() + 1 < this.totalPages());

  label(name: string): string {
    return queueDisplay(name).label;
  }

  description(name: string): string {
    return queueDisplay(name).description;
  }

  subject(row: QueueMessageSummary): string {
    return subjectLabel(this.activeQueueName() ?? '', row.subject);
  }

  /** The card's status bar: each non-empty status's share of every message on the queue. */
  segments(queue: QueueSummary): StatusSegment[] {
    const total = totalMessages(queue);
    return this.statuses
      .map((status) => ({ status, count: queue.statusCounts[status] ?? 0 }))
      .filter((segment) => segment.count > 0)
      .map((segment) => ({ ...segment, percent: (segment.count / total) * 100 }));
  }

  count(status: OutboxMessageStatus | null): number {
    const queue = this.activeQueue();
    if (!queue) {
      return 0;
    }
    return status ? (queue.statusCounts[status] ?? 0) : totalMessages(queue);
  }

  selectQueue(name: string): void {
    this.chosenQueue.set(name);
    this.resetList();
  }

  /** Filters the list to one status; choosing the active filter again clears it. */
  toggleStatus(status: OutboxMessageStatus | null): void {
    this.statusFilter.update((current) => (current === status ? null : status));
    this.resetList();
  }

  selectMessage(id: string): void {
    this.selectedMessageId.set(id);
  }

  isSelected(id: string): boolean {
    return this.selectedMessageId() === id;
  }

  previousPage(): void {
    this.page.update((page) => (this.canGoPrevious() ? page - 1 : page));
  }

  nextPage(): void {
    this.page.update((page) => (this.canGoNext() ? page + 1 : page));
  }

  private resetList(): void {
    this.page.set(0);
    this.selectedMessageId.set(null);
  }

  private fetchQueues(): Observable<QueueSummary[] | 'unreachable'> {
    return this.queueService.queues().pipe(
      map((queues): QueueSummary[] | 'unreachable' => queues),
      catchError(() => of('unreachable' as const))
    );
  }

  private pollMessages(
    queue: string,
    status: OutboxMessageStatus | null,
    page: number
  ): Observable<QueueMessagePage> {
    return timer(0, QUEUE_POLL_INTERVAL_MS).pipe(
      switchMap(() =>
        this.queueService
          .messages(queue, page, this.pageSize, status)
          .pipe(catchError(() => of(EMPTY_PAGE)))
      )
    );
  }
}
