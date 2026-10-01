import { DatePipe } from '@angular/common';
import { Component, computed, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { EMPTY, Observable, of, timer } from 'rxjs';
import { catchError, switchMap, takeWhile } from 'rxjs/operators';
import { ActiveWorkflowsService } from '../../active-workflows.service';
import { TaskAttempt } from '../../task-attempt.model';
import {
  STATUS_MEANING,
  asLlmRequest,
  clientPromptIdOf,
  deferredUntilOf,
  isOpen,
  lifecycleSteps,
  prettyJson,
  taskAttemptIdOf
} from './queue-display';
import { QueueMessage } from './queue.model';
import { QueueService } from './queue.service';

export const MESSAGE_POLL_INTERVAL_MS = 3000;

/**
 * Everything about one queue message: where it is in its lifecycle, why it failed if it did, and
 * the data it carries -- the prompt text, the task attempt, or the LLM request and response.
 *
 * <p>Polls the message while it can still change and stops once it has been processed or failed,
 * so a settled LLM request's (possibly large) payload is fetched once rather than every poll.</p>
 */
@Component({
  selector: 'app-queue-message-detail',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './queue-message-detail.component.html',
  styleUrl: './queue-message-detail.component.css'
})
export class QueueMessageDetailComponent {
  private queueService = inject(QueueService);
  private activeWorkflowsService = inject(ActiveWorkflowsService);

  queue = input.required<string>();
  messageId = input.required<string>();

  private selection = computed(() => ({ queue: this.queue(), id: this.messageId() }));

  /** `undefined` while the first fetch is in flight; `null` once the message is gone. */
  readonly message = toSignal(
    toObservable(this.selection).pipe(
      switchMap(({ queue, id }) => this.pollMessage(queue, id))
    ),
    { initialValue: undefined }
  );

  readonly steps = computed(() => this.whenLoaded((message) => lifecycleSteps(message), []));
  readonly statusMeaning = computed(() =>
    this.whenLoaded((message) => STATUS_MEANING[message.status], '')
  );
  readonly deferredUntil = computed(() => this.whenLoaded(deferredUntilOf, null));
  readonly llmRequest = computed(() => this.whenLoaded(asLlmRequest, null));
  readonly requestJson = computed(() => prettyJson(this.llmRequest()?.requestJson));
  readonly responseJson = computed(() => prettyJson(this.llmRequest()?.responseJson));

  readonly clientPromptId = computed(() => this.whenLoaded(clientPromptIdOf, null));
  readonly promptText = toSignal(
    toObservable(this.clientPromptId).pipe(
      switchMap((id) => (id ? this.orNull(this.activeWorkflowsService.promptText(id)) : of(null)))
    ),
    { initialValue: null }
  );

  readonly taskAttemptId = computed(() => this.whenLoaded(taskAttemptIdOf, null));
  readonly taskAttempt = toSignal(
    toObservable(this.taskAttemptId).pipe(
      switchMap((id) => (id ? this.orNull(this.queueService.taskAttempt(id)) : of(null)))
    ),
    { initialValue: null as TaskAttempt | null }
  );

  private pollMessage(queue: string, id: string): Observable<QueueMessage | null> {
    return timer(0, MESSAGE_POLL_INTERVAL_MS).pipe(
      switchMap(() => this.queueService.message(queue, id).pipe(catchError(() => EMPTY))),
      takeWhile((message) => !!message && isOpen(message.status), true)
    );
  }

  private orNull<T>(source: Observable<T>): Observable<T | null> {
    return source.pipe(catchError(() => of(null)));
  }

  private whenLoaded<T>(read: (message: QueueMessage) => T, fallback: T): T {
    const message = this.message();
    return message ? read(message) : fallback;
  }
}
