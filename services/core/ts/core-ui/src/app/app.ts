import { HttpErrorResponse } from '@angular/common/http';
import { Component, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ReactiveFormsModule, FormControl, Validators } from '@angular/forms';
import { firstValueFrom, of, timer } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { ClientPromptService } from './client-prompt.service';
import { BackPressure, OrchestratorError } from './client-prompt.model';
import { PendingWorkflowRegistry } from './workflow-updates/pending-workflow.registry';
import { WorkflowPanelComponent } from './workflow-panel/workflow-panel.component';

const BACKPRESSURE_POLL_INTERVAL_MS = 5000;

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [ReactiveFormsModule, WorkflowPanelComponent],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  private svc = inject(ClientPromptService);
  private pendingWorkflows = inject(PendingWorkflowRegistry);
  private fileInput = viewChild<ElementRef<HTMLInputElement>>('fileInput');

  title = 'Vader Core UI';

  readonly maxFiles = 5;

  text = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(2000)]
  });

  private textValue = toSignal(this.text.valueChanges, {
    initialValue: this.text.value
  });

  readonly chars = computed(() => this.textValue()?.length ?? 0);
  readonly max = 2000;

  files = signal<File[]>([]);
  fileError = signal<string | null>(null);

  sending = signal(false);
  sent = signal(false);
  error = signal<string | null>(null);

  /** The most recently submitted prompt's id, so the panel can auto-expand its workflow. */
  lastSubmittedPromptId = signal<string | null>(null);

  /** True while a submitted prompt has no Workflow row back yet — blocks a second submission so
   *  the panel only ever has to render one in-flight placeholder at a time. */
  readonly hasPendingWorkflow = computed(() => this.pendingWorkflows.pending() !== null);

  backPressure = toSignal(
    timer(0, BACKPRESSURE_POLL_INTERVAL_MS).pipe(
      switchMap(() => this.svc.getBackpressure().pipe(catchError(() => of(null))))
    ),
    { initialValue: null as BackPressure | null }
  );

  constructor() {
    document.addEventListener('keydown', (e) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') this.submit();
    });
  }

  onFilesSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    const selected = input.files ? Array.from(input.files) : [];
    if (selected.length > this.maxFiles) {
      this.fileError.set(`Too many files. Maximum is ${this.maxFiles}; please select up to ${this.maxFiles} files.`);
      this.files.set([]);
      input.value = '';
      return;
    }
    this.fileError.set(null);
    this.files.set(selected);
  }

  /** Submits the prompt. Blocked while a previous prompt is still waiting on its Workflow row,
   *  so at most one workflow is ever in flight from the form's perspective — that lets the panel
   *  render an unambiguous placeholder for it until the server responds with the hydrated
   *  workflow and its UUID. */
  async submit() {
    this.error.set(null);
    this.sent.set(false);
    if (this.text.invalid || this.sending() || this.fileError() || this.hasPendingWorkflow()) {
      return;
    }

    this.sending.set(true);
    try {
      const res = await firstValueFrom(this.svc.postPrompt({
        modelType: 'ClientPrompt',
        text: this.text.value,
        files: this.files()
      }));
      if (res.status !== 202 || !res.body) {
        throw new Error(`Unexpected status: ${res.status}`);
      }
      this.sent.set(true);
      this.lastSubmittedPromptId.set(res.body.id);
      this.pendingWorkflows.register(res.body.id);
      this.text.reset('');
      this.files.set([]);
      const inputEl = this.fileInput();
      if (inputEl) inputEl.nativeElement.value = '';
    } catch (e: unknown) {
      this.error.set(this.extractErrorMessage(e));
    } finally {
      this.sending.set(false);
    }
  }

  private extractErrorMessage(e: unknown): string {
    if (e instanceof HttpErrorResponse) {
      const body = e.error as OrchestratorError | null;
      return body?.message ?? e.message;
    }
    if (e instanceof Error) {
      return e.message;
    }
    return 'Failed to send prompt';
  }
}
