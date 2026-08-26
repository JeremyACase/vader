import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ClientPrompt } from './client-prompt.model';

@Injectable({ providedIn: 'root' })
export class ClientPromptService {
  private http = inject(HttpClient);

  postPrompt(prompt: ClientPrompt): Observable<HttpResponse<void>> {
    const formData = new FormData();
    formData.append('text', prompt.text);
    for (const file of prompt.files ?? []) {
      formData.append('files', file, file.name);
    }

    return this.http.post<void>('/vader/core-server/client-prompt', formData, {
      observe: 'response'
    });
  }
}
