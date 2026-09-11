import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { BackPressure, ClientPrompt, IngressResponse } from './client-prompt.model';

@Injectable({ providedIn: 'root' })
export class ClientPromptService {
  private http = inject(HttpClient);

  /** Submits a prompt. Resolves to the 202 receipt; the workflow is built asynchronously. */
  postPrompt(prompt: ClientPrompt): Observable<HttpResponse<IngressResponse>> {
    const formData = new FormData();
    formData.append('text', prompt.text);
    for (const file of prompt.files ?? []) {
      formData.append('files', file, file.name);
    }

    return this.http.post<IngressResponse>('/vader/core-server/client-prompt', formData, {
      observe: 'response'
    });
  }

  /** Fetches how backed up the decomposition queue is. */
  getBackpressure(modelType = 'ClientPrompt'): Observable<BackPressure> {
    return this.http.get<BackPressure>(`/vader/core-server/backpressure/${modelType}`);
  }
}
