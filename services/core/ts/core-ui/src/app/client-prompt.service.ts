import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { BackPressure, ClientPrompt, IngressResponse, Workflow } from './client-prompt.model';

/** The subset of the DAO query response the UI reads. */
interface WorkflowPage {
  content: Workflow[];
}

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

  /** Fetches the workflow decomposed for a prompt, or null if it does not exist yet. */
  getWorkflowByPromptId(promptId: string): Observable<Workflow | null> {
    const params = new HttpParams()
      .set('page', '0')
      .set('size', '1')
      .set('sort-descending', 'true')
      .set('sort-by-fields', 'createdAt')
      .set('clientPrompt.id', promptId);

    return this.http
      .get<WorkflowPage>('/vader/core-server/data/workflow/query/params', { params })
      .pipe(map((page) => page.content[0] ?? null));
  }

  /** Fetches how backed up the decomposition queue is. */
  getBackpressure(modelType = 'ClientPrompt'): Observable<BackPressure> {
    return this.http.get<BackPressure>(`/vader/core-server/backpressure/${modelType}`);
  }
}
