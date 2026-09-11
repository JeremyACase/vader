import { InjectionToken } from '@angular/core';
import { InterfaceWorkflowUpdatesStrategy } from './workflow-updates.strategy';

/** DI seam selecting how the active-workflows panel receives its data; see app.config.ts. */
export const WORKFLOW_UPDATES_STRATEGY = new InjectionToken<InterfaceWorkflowUpdatesStrategy>(
  'WORKFLOW_UPDATES_STRATEGY'
);
