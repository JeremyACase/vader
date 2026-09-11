import { Component, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ReactiveFormsModule, FormControl, Validators } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ClientPromptService } from './client-prompt.service';
import { BackPressure, Workflow } from './client-prompt.model';

const POLL_INTERVAL_MS = 1500;
const POLL_TIMEOUT_MS = 60_000;

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  private svc = inject(ClientPromptService);
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
  pending = signal(false);
  error = signal<string | null>(null);
  plan = signal<Workflow | null>(null);
  backPressure = signal<BackPressure | null>(null);

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

  async submit() {
    this.error.set(null);
    this.sent.set(false);
    this.plan.set(null);
    this.pending.set(false);
    this.backPressure.set(null);
    if (this.text.invalid || this.sending() || this.fileError()) return;

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
      this.text.reset('');
      this.files.set([]);
      const inputEl = this.fileInput();
      if (inputEl) inputEl.nativeElement.value = '';
      await this.awaitWorkflow(res.body.id);
    } catch (e: any) {
      const msg = e?.error?.message || e?.message || 'Failed to send prompt';
      this.error.set(String(msg));
    } finally {
      this.sending.set(false);
      this.pending.set(false);
    }
  }

  private async awaitWorkflow(promptId: string): Promise<void> {
    this.pending.set(true);
    const deadline = Date.now() + POLL_TIMEOUT_MS;
    while (Date.now() < deadline) {
      const workflow = await firstValueFrom(this.svc.getWorkflowByPromptId(promptId));
      if (workflow?.taskPlan) {
        this.plan.set(workflow);
        return;
      }
      this.backPressure.set(await this.readBackpressure());
      await this.delay(POLL_INTERVAL_MS);
    }
    throw new Error(
      'Timed out waiting for the decomposition. It may still complete — query the workflow later.'
    );
  }

  private async readBackpressure(): Promise<BackPressure | null> {
    try {
      return await firstValueFrom(this.svc.getBackpressure());
    } catch {
      return null;
    }
  }

  private delay(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }
}
