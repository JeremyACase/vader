import { ApplicationConfig, provideBrowserGlobalErrorListeners, provideZoneChangeDetection } from '@angular/core';
import { provideHttpClient, withFetch } from '@angular/common/http';
import { LongPollWorkflowUpdatesStrategy } from './workflow-updates/long-poll-workflow-updates.strategy';
import { WORKFLOW_UPDATES_STRATEGY } from './workflow-updates/workflow-updates.token';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideHttpClient(withFetch()),
    // Swap to WebSocketWorkflowUpdatesStrategy once a BFF exists to push these updates instead.
    { provide: WORKFLOW_UPDATES_STRATEGY, useClass: LongPollWorkflowUpdatesStrategy }
  ]
};
