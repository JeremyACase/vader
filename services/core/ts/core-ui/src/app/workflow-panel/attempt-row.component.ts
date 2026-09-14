import { Component, computed, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { switchMap } from 'rxjs/operators';
import { ActiveWorkflowsService } from '../active-workflows.service';
import { TaskAttempt, TaskAttemptTranscript } from '../task-attempt.model';

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
}
