import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { TaskAttempt } from '../../task-attempt.model';
import { OutboxMessageStatus, QueueMessage, QueueMessagePage, QueueSummary } from './queue.model';

const QUEUE_URL = '/vader/core-server/queue';

/** Read-only access to core-server's inbox/outbox queue inspection endpoint. */
@Injectable({ providedIn: 'root' })
export class QueueService {
  private http = inject(HttpClient);

  /** Every queue with its per-status message counts. */
  queues(): Observable<QueueSummary[]> {
    return this.http.get<QueueSummary[]>(QUEUE_URL);
  }

  /** One page of a queue's messages, newest first, optionally only those in one status. */
  messages(
    queue: string,
    page: number,
    size: number,
    status: OutboxMessageStatus | null
  ): Observable<QueueMessagePage> {
    const base = new HttpParams().set('page', String(page)).set('size', String(size));
    const params = status ? base.set('status', status) : base;
    return this.http.get<QueueMessagePage>(`${QUEUE_URL}/${queue}/message`, { params });
  }

  /** One message in full, payload included; `null` once it no longer exists (a 204). */
  message(queue: string, id: string): Observable<QueueMessage | null> {
    return this.http.get<QueueMessage | null>(`${QUEUE_URL}/${queue}/message/${id}`);
  }

  /** The task attempt a task-assignment or attempt-review message carries; `null` if gone. */
  taskAttempt(id: string): Observable<TaskAttempt | null> {
    return this.http
      .get<TaskAttempt | null>(`/vader/core-server/data/task-attempt/query/${id}`)
      .pipe(map((attempt) => attempt ?? null));
  }
}
