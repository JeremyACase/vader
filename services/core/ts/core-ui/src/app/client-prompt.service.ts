import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ClientPrompt, Workflow } from './client-prompt.model';

@Injectable({ providedIn: 'root' })
export class ClientPromptService {
  private http = inject(HttpClient);

  postPrompt(prompt: ClientPrompt): Observable<HttpResponse<Workflow>> {
    const formData = new FormData();
    formData.append('text', prompt.text);
    for (const file of prompt.files ?? []) {
      formData.append('files', file, file.name);
    }

    return this.http.post<Workflow>('/vader/core-server/client-prompt', formData, {
      observe: 'response'
    });
  }
}
