import { Observable } from 'rxjs';
import { Workflow } from '../client-prompt.model';
import { TaskAttempt, TaskAttemptTranscript } from '../task-attempt.model';

/**
 * Strategy for keeping the active-workflows panel current. {@link LongPollWorkflowUpdatesStrategy}
 * polls core-server's DAO query endpoints directly; {@link WebSocketWorkflowUpdatesStrategy} will
 * instead subscribe to a BFF pushing the same shapes over a socket once one exists. Callers
 * (`ActiveWorkflowsService` and the panel components) depend only on this interface, so swapping
 * strategies is a one-line change to the `WORKFLOW_UPDATES_STRATEGY` provider in app.config.ts.
 */
export interface InterfaceWorkflowUpdatesStrategy {
  /**
   * The most recently created workflows, most-recent first — regardless of status, so a
   * workflow that just failed or succeeded stays visible instead of disappearing from the panel
   * the moment it leaves `RUNNING`.
   */
  recentWorkflows(): Observable<Workflow[]>;

  /** A task's attempt history, most recent attempt first. */
  taskAttempts(taskId: string): Observable<TaskAttempt[]>;

  /** One attempt's inference turns (the chain-of-thought), in turn order. */
  transcripts(taskAttemptId: string): Observable<TaskAttemptTranscript[]>;

  /** The original prompt text a workflow was spawned from. */
  promptText(clientPromptId: string): Observable<string>;
}
