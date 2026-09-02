import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ReactiveFormsModule, FormControl, Validators } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ClientPromptService } from './client-prompt.service';
import { Workflow } from './client-prompt.model';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  private svc = inject(ClientPromptService);

  title = 'Vader Core UI';

  text = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(2000)]
  });

  private textValue = toSignal(this.text.valueChanges, {
    initialValue: this.text.value
  });

  readonly chars = computed(() => this.textValue()?.length ?? 0);
  readonly max = 2000;

  private files: File[] = [];

  sending = signal(false);
  sent = signal(false);
  error = signal<string | null>(null);
  plan = signal<Workflow | null>(null);

  constructor() {
    document.addEventListener('keydown', (e) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') this.submit();
    });
  }

  onFilesSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    this.files = input.files ? Array.from(input.files) : [];
  }

  async submit() {
    this.error.set(null);
    this.sent.set(false);
    this.plan.set(null);
    if (this.text.invalid || this.sending()) return;

    this.sending.set(true);
    try {
      const res = await firstValueFrom(this.svc.postPrompt({
        modelType: 'ClientPrompt',
        text: this.text.value,
        files: this.files
      }));
      if (res.status < 200 || res.status >= 300) {
        throw new Error(`Non-2xx status: ${res.status}`);
      }
      this.sent.set(true);
      this.plan.set(res.body);
      this.text.reset('');
      this.files = [];
    } catch (e: any) {
      const msg = e?.error?.message || e?.message || 'Failed to send prompt';
      this.error.set(String(msg));
    } finally {
      this.sending.set(false);
    }
  }
}
