import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, timer } from 'rxjs';
import { map, shareReplay, switchMap } from 'rxjs/operators';
import { ClientPrompt, Workflow } from '../client-prompt.model';
import { DaoPage } from '../dao-page.model';
import { TaskAttempt, TaskAttemptTranscript } from '../task-attempt.model';
import { InterfaceWorkflowUpdatesStrategy } from './workflow-updates.strategy';

const POLL_INTERVAL_MS = 3000;

/**
 * Polls core-server's generic DAO query endpoints directly. This is the strategy used until a
 * BFF exists to aggregate and push this data instead (see {@link WebSocketWorkflowUpdatesStrategy}).
 *
 * <p>Every polling stream is cached per resource key and shared via `shareReplay({ refCount:
 * true })`, so: polling for a given task/attempt/prompt only runs while at least one panel row
 * has it expanded (subscribed), and stops the moment it's collapsed; and two rows asking about
 * the same resource share one poll rather than duplicating requests.
 */
@Injectable({ providedIn: 'root' })
export class LongPollWorkflowUpdatesStrategy implements InterfaceWorkflowUpdatesStrategy {
  private http = inject(HttpClient);

  private readonly recentWorkflows$ = this.poll<Workflow[]>(() =>
    this.http
      .get<DaoPage<Workflow>>('/vader/core-server/data/workflow/query/params', {
        params: new HttpParams()
          .set('page', '0')
          .set('size', '50')
          .set('sort-descending', 'true')
          .set('sort-by-fields', 'createdAt')
      })
      .pipe(map((page) => page.content))
  );

  private readonly taskAttemptsCache = new Map<string, Observable<TaskAttempt[]>>();
  private readonly transcriptsCache = new Map<string, Observable<TaskAttemptTranscript[]>>();
  private readonly promptTextCache = new Map<string, Observable<string>>();

  recentWorkflows(): Observable<Workflow[]> {
    return this.recentWorkflows$;
  }

  taskAttempts(taskId: string): Observable<TaskAttempt[]> {
    return this.cached(this.taskAttemptsCache, taskId, () =>
      this.poll(() =>
        this.http
          .get<DaoPage<TaskAttempt>>('/vader/core-server/data/task-attempt/query/params', {
            params: new HttpParams()
              .set('page', '0')
              .set('size', '20')
              .set('sort-descending', 'true')
              .set('sort-by-fields', 'attemptNumber')
              .set('task.id', taskId)
          })
          .pipe(map((page) => page.content))
      )
    );
  }

  transcripts(taskAttemptId: string): Observable<TaskAttemptTranscript[]> {
    return this.cached(this.transcriptsCache, taskAttemptId, () =>
      this.poll(() =>
        this.http
          .get<DaoPage<TaskAttemptTranscript>>(
            '/vader/core-server/data/task-attempt-transcript/query/params',
            {
              params: new HttpParams()
                .set('page', '0')
                .set('size', '100')
                .set('sort-descending', 'false')
                .set('sort-by-fields', 'turnIndex')
                .set('taskAttempt.id', taskAttemptId)
            }
          )
          .pipe(map((page) => page.content))
      )
    );
  }

  promptText(clientPromptId: string): Observable<string> {
    return this.cached(this.promptTextCache, clientPromptId, () =>
      this.http
        .get<ClientPrompt>(`/vader/core-server/data/client-prompt/query/${clientPromptId}`)
        .pipe(
          map((prompt) => prompt.text),
          shareReplay({ bufferSize: 1, refCount: false })
        )
    );
  }

  private cached<T>(
    cache: Map<string, Observable<T>>,
    key: string,
    create: () => Observable<T>
  ): Observable<T> {
    let existing = cache.get(key);
    if (!existing) {
      existing = create();
      cache.set(key, existing);
    }
    return existing;
  }

  private poll<T>(request: () => Observable<T>): Observable<T> {
    return timer(0, POLL_INTERVAL_MS).pipe(
      switchMap(request),
      shareReplay({ bufferSize: 1, refCount: true })
    );
  }
}
