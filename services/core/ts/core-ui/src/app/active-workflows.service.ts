import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Workflow } from './client-prompt.model';
import { DaoPage } from './dao-page.model';
import { TaskAttempt, TaskAttemptTranscript } from './task-attempt.model';
import { TaskUpdate } from './task-update.model';
import { WORKFLOW_UPDATES_STRATEGY } from './workflow-updates/workflow-updates.token';

/**
 * Facade over the active {@link InterfaceWorkflowUpdatesStrategy}. Components depend on this
 * service rather than the strategy token directly, so the panel is agnostic to whether updates
 * are currently long-polled or (once a BFF exists) pushed over a WebSocket.
 */
@Injectable({ providedIn: 'root' })
export class ActiveWorkflowsService {
  private strategy = inject(WORKFLOW_UPDATES_STRATEGY);

  recentWorkflows(page: number, pageSize: number): Observable<DaoPage<Workflow>> {
    return this.strategy.recentWorkflows(page, pageSize);
  }

  workflow(workflowId: string): Observable<Workflow> {
    return this.strategy.workflow(workflowId);
  }

  taskAttempts(taskId: string): Observable<TaskAttempt[]> {
    return this.strategy.taskAttempts(taskId);
  }

  transcripts(taskAttemptId: string): Observable<TaskAttemptTranscript[]> {
    return this.strategy.transcripts(taskAttemptId);
  }

  taskUpdates(taskId: string): Observable<TaskUpdate[]> {
    return this.strategy.taskUpdates(taskId);
  }

  promptText(clientPromptId: string): Observable<string> {
    return this.strategy.promptText(clientPromptId);
  }
}
