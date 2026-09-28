import { Component, computed, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { switchMap } from 'rxjs/operators';
import { ActiveWorkflowsService } from '../active-workflows.service';
import { TaskAttempt, TaskAttemptTranscript } from '../task-attempt.model';

/** The finish reason a provider reports when a reply was cut off at the output token cap. */
const OUTPUT_CAP_FINISH_REASON = 'length';

/**
 * Renders one task attempt's outcome: its final result (when the harness reported one) and its
 * chain-of-thought, the inference turns that led to it, in order.
 */
@Component({
  selector: 'app-attempt-row',
  standalone: true,
  imports: [],
  templateUrl: './attempt-row.component.html',
  styleUrl: './attempt-row.component.css'
})
export class AttemptRowComponent {
  private activeWorkflowsService = inject(ActiveWorkflowsService);

  attempt = input.required<TaskAttempt>();

  private attemptId = computed(() => this.attempt().id ?? '');

  transcripts = toSignal(
    toObservable(this.attemptId).pipe(
      switchMap((id) => this.activeWorkflowsService.transcripts(id))
    ),
    { initialValue: [] as TaskAttemptTranscript[] }
  );

  /** Whether this turn's reply was cut off at the output token cap, and so is incomplete. */
  isCutOff(turn: TaskAttemptTranscript): boolean {
    return turn.finishReason === OUTPUT_CAP_FINISH_REASON;
  }

  /** A short label for why the model stopped generating this turn; empty when none was recorded. */
  finishLabel(turn: TaskAttemptTranscript): string {
    const reason = turn.finishReason ?? '';
    const cutOffLabel = 'cut off at the output token cap';
    const label = reason ? `finished: ${reason}` : '';
    return this.isCutOff(turn) ? cutOffLabel : label;
  }
}
