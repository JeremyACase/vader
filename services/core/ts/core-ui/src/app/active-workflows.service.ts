import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Workflow } from './client-prompt.model';
import { TaskAttempt, TaskAttemptTranscript } from './task-attempt.model';
import { WORKFLOW_UPDATES_STRATEGY } from './workflow-updates/workflow-updates.token';

/**
 * Facade over the active {@link InterfaceWorkflowUpdatesStrategy}. Components depend on this
 * service rather than the strategy token directly, so the panel is agnostic to whether updates
 * are currently long-polled or (once a BFF exists) pushed over a WebSocket.
 */
@Injectable({ providedIn: 'root' })
export class ActiveWorkflowsService {
  private strategy = inject(WORKFLOW_UPDATES_STRATEGY);

  activeWorkflows(): Observable<Workflow[]> {
    return this.strategy.activeWorkflows();
  }

  taskAttempts(taskId: string): Observable<TaskAttempt[]> {
    return this.strategy.taskAttempts(taskId);
  }

  transcripts(taskAttemptId: string): Observable<TaskAttemptTranscript[]> {
    return this.strategy.transcripts(taskAttemptId);
  }

  promptText(clientPromptId: string): Observable<string> {
    return this.strategy.promptText(clientPromptId);
  }
}
