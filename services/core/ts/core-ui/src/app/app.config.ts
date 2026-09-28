import { ApplicationConfig, provideBrowserGlobalErrorListeners, provideZoneChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { LongPollWorkflowUpdatesStrategy } from './workflow-updates/long-poll-workflow-updates.strategy';
import { WORKFLOW_UPDATES_STRATEGY } from './workflow-updates/workflow-updates.token';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),
    // Deliberately the XHR backend (the default when withFetch() is omitted), not withFetch():
    // this app's every live-updating view (workflow list, DAG, attempts, updates) depends on
    // zone.js noticing each long-poll response to trigger change detection, and zone.js's
    // XMLHttpRequest patch is reliable where its fetch patch is not -- with withFetch(), poll
    // responses could resolve outside the Angular zone, updating signals with no repaint to
    // show it until some unrelated zone-patched event (or a full reload) forced one.
    provideHttpClient(),
    // Swap to WebSocketWorkflowUpdatesStrategy once a BFF exists to push these updates instead.
    { provide: WORKFLOW_UPDATES_STRATEGY, useClass: LongPollWorkflowUpdatesStrategy }
  ]
};
