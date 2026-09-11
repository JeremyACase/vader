import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { WebSocketSubject, webSocket } from 'rxjs/webSocket';
import { Workflow } from '../client-prompt.model';
import { TaskAttempt, TaskAttemptTranscript } from '../task-attempt.model';
import { InterfaceWorkflowUpdatesStrategy } from './workflow-updates.strategy';

/** One multiplexed message on the BFF's proposed workflow-updates socket. */
interface WorkflowUpdateMessage<T> {
  channel: 'active-workflows' | 'task-attempts' | 'transcripts' | 'prompt-text';
  key: string | null;
  payload: T;
}

/**
 * Speculative BFF-backed strategy: a single multiplexed WebSocket, demultiplexed by
 * `channel`/`key`, replacing core-server's per-resource long polling once a BFF exists to push
 * this data instead of the panel pulling it. Not selected by default — there is no BFF to
 * connect to yet, and the {@link WorkflowUpdateMessage} envelope is a proposal, not a protocol
 * any server currently implements. Swap the `WORKFLOW_UPDATES_STRATEGY` provider in app.config.ts
 * from `LongPollWorkflowUpdatesStrategy` to this class once a BFF implements it.
 */
@Injectable({ providedIn: 'root' })
export class WebSocketWorkflowUpdatesStrategy implements InterfaceWorkflowUpdatesStrategy {
  private socket: WebSocketSubject<WorkflowUpdateMessage<unknown>> | null = null;

  activeWorkflows(): Observable<Workflow[]> {
    return this.channel<Workflow[]>('active-workflows', null);
  }

  taskAttempts(taskId: string): Observable<TaskAttempt[]> {
    return this.channel<TaskAttempt[]>('task-attempts', taskId);
  }

  transcripts(taskAttemptId: string): Observable<TaskAttemptTranscript[]> {
    return this.channel<TaskAttemptTranscript[]>('transcripts', taskAttemptId);
  }

  promptText(clientPromptId: string): Observable<string> {
    return this.channel<string>('prompt-text', clientPromptId);
  }

  private channel<T>(
    channel: WorkflowUpdateMessage<T>['channel'],
    key: string | null
  ): Observable<T> {
    return this.connection().pipe(
      filter((message) => message.channel === channel && message.key === key),
      map((message) => message.payload as T)
    );
  }

  private connection(): WebSocketSubject<WorkflowUpdateMessage<unknown>> {
    if (!this.socket) {
      this.socket = webSocket<WorkflowUpdateMessage<unknown>>(this.socketUrl());
    }
    return this.socket;
  }

  private socketUrl(): string {
    const protocol = location.protocol === 'https:' ? 'wss' : 'ws';
    return `${protocol}://${location.host}/vader/bff/workflow-updates`;
  }
}
