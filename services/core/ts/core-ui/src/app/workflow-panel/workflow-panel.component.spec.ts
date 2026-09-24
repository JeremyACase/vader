import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, Subject } from 'rxjs';
import { ActiveWorkflowsService } from '../active-workflows.service';
import { Workflow } from '../client-prompt.model';
import { DaoPage } from '../dao-page.model';
import { TaskAttempt, TaskAttemptTranscript } from '../task-attempt.model';
import { TaskUpdate } from '../task-update.model';
import { PendingWorkflowRegistry } from '../workflow-updates/pending-workflow.registry';
import { WorkflowPanelComponent } from './workflow-panel.component';

function workflow(overrides: Partial<Workflow>): Workflow {
  return {
    id: 'wf-1',
    clientPromptId: 'prompt-1',
    status: 'RUNNING',
    createdAt: '2026-01-01T00:00:00Z',
    ...overrides
  };
}

function page(content: Workflow[], totalElements = content.length): DaoPage<Workflow> {
  return { content, totalElements };
}

class FakeActiveWorkflowsService {
  workflowsPage$ = new Subject<DaoPage<Workflow>>();

  recentWorkflows(): Observable<DaoPage<Workflow>> {
    return this.workflowsPage$.asObservable();
  }

  workflow(): Observable<Workflow> {
    return new Subject<Workflow>().asObservable();
  }

  taskAttempts(): Observable<TaskAttempt[]> {
    return new Subject<TaskAttempt[]>().asObservable();
  }

  transcripts(): Observable<TaskAttemptTranscript[]> {
    return new Subject<TaskAttemptTranscript[]>().asObservable();
  }

  taskUpdates(): Observable<TaskUpdate[]> {
    return new Subject<TaskUpdate[]>().asObservable();
  }

  promptText(): Observable<string> {
    return new Subject<string>().asObservable();
  }
}

describe('WorkflowPanelComponent', () => {
  let fixture: ComponentFixture<WorkflowPanelComponent>;
  let component: WorkflowPanelComponent;
  let fakeService: FakeActiveWorkflowsService;
  let pendingWorkflows: PendingWorkflowRegistry;

  beforeEach(() => {
    fakeService = new FakeActiveWorkflowsService();
    TestBed.configureTestingModule({
      imports: [WorkflowPanelComponent],
      providers: [{ provide: ActiveWorkflowsService, useValue: fakeService }]
    });
    fixture = TestBed.createComponent(WorkflowPanelComponent);
    component = fixture.componentInstance;
    pendingWorkflows = TestBed.inject(PendingWorkflowRegistry);
  });

  it('lists the workflows most-recently-created first, as the server returns them', () => {
    fixture.detectChanges();
    fakeService.workflowsPage$.next(
      page([
        workflow({ id: 'wf-new', createdAt: '2026-01-02T00:00:00Z' }),
        workflow({ id: 'wf-old', createdAt: '2026-01-01T00:00:00Z' })
      ])
    );
    fixture.detectChanges();

    expect(component.sortedWorkflows().map((w) => w.id)).toEqual(['wf-new', 'wf-old']);
  });

  it('badges a workflow that is waiting out an LLM outage as AWAITING_LLM', () => {
    fixture.detectChanges();
    fakeService.workflowsPage$.next(page([workflow({ id: 'wf-1', status: 'AWAITING_LLM' })]));
    fixture.detectChanges();

    const badge: HTMLElement | null = fixture.nativeElement.querySelector('.status-badge');
    expect(badge?.getAttribute('data-status')).toBe('AWAITING_LLM');
    expect(badge?.textContent?.trim()).toBe('AWAITING_LLM');
  });

  it('selects a real workflow row and emits its id', () => {
    fixture.detectChanges();
    let emitted: string | undefined;
    component.workflowSelected.subscribe((id) => (emitted = id));
    fakeService.workflowsPage$.next(page([workflow({ id: 'wf-1' })]));
    fixture.detectChanges();

    expect(component.isSelected('wf-1')).toBeFalse();
    component.select(workflow({ id: 'wf-1' }));

    expect(component.isSelected('wf-1')).toBeTrue();
    expect(emitted).toBe('wf-1');
  });

  it('ignores a click on the pending placeholder row', () => {
    fixture.detectChanges();
    pendingWorkflows.register('prompt-pending');
    fixture.detectChanges();
    let emitted = false;
    component.workflowSelected.subscribe(() => (emitted = true));

    const placeholder = component.sortedWorkflows()[0];
    component.select(placeholder);

    expect(emitted).toBeFalse();
  });

  it('auto-selects the just-submitted prompt once its real workflow appears, only once', () => {
    fixture.componentRef.setInput('justSubmittedPromptId', 'prompt-1');
    fixture.detectChanges();
    const emitted: string[] = [];
    component.workflowSelected.subscribe((id) => emitted.push(id));

    fakeService.workflowsPage$.next(page([workflow({ id: 'wf-1', clientPromptId: 'prompt-1' })]));
    fixture.detectChanges();
    fakeService.workflowsPage$.next(page([workflow({ id: 'wf-1', clientPromptId: 'prompt-1' })]));
    fixture.detectChanges();

    expect(emitted).toEqual(['wf-1']);
  });

  it('shows a placeholder row for a pending prompt with no Workflow row yet', () => {
    fixture.detectChanges();
    pendingWorkflows.register('prompt-pending');
    fixture.detectChanges();

    const placeholder = component.sortedWorkflows().find(
      (w) => w.clientPromptId === 'prompt-pending'
    );
    expect(placeholder).toBeTruthy();
    expect(placeholder?.status).toBe('RUNNING');
    expect(placeholder?.taskPlan).toBeUndefined();
  });

  it('drops the placeholder once the real workflow for that prompt appears', () => {
    fixture.detectChanges();
    pendingWorkflows.register('prompt-pending');
    fixture.detectChanges();

    fakeService.workflowsPage$.next(page([workflow({ id: 'wf-real', clientPromptId: 'prompt-pending' })]));
    fixture.detectChanges();

    const matches = component
      .sortedWorkflows()
      .filter((w) => w.clientPromptId === 'prompt-pending');
    expect(matches.length).toBe(1);
    expect(matches[0].id).toBe('wf-real');
  });

  it('paginates: next/previous move the page and are bounded', () => {
    fixture.detectChanges();
    fakeService.workflowsPage$.next(page([workflow({ id: 'wf-1' })], 25));
    fixture.detectChanges();

    expect(component.canGoPrevious()).toBeFalse();
    expect(component.canGoNext()).toBeTrue();
    expect(component.totalPages()).toBe(3);

    component.nextPage();
    expect(component.page()).toBe(1);
    component.previousPage();
    expect(component.page()).toBe(0);

    component.previousPage();
    expect(component.page()).toBe(0);
  });
});
