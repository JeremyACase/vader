import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, Subject } from 'rxjs';
import { ActiveWorkflowsService } from '../active-workflows.service';
import { Workflow } from '../client-prompt.model';
import { TaskAttempt, TaskAttemptTranscript } from '../task-attempt.model';
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

class FakeActiveWorkflowsService {
  workflows$ = new Subject<Workflow[]>();

  recentWorkflows(): Observable<Workflow[]> {
    return this.workflows$.asObservable();
  }

  taskAttempts(): Observable<TaskAttempt[]> {
    return new Subject<TaskAttempt[]>().asObservable();
  }

  transcripts(): Observable<TaskAttemptTranscript[]> {
    return new Subject<TaskAttemptTranscript[]>().asObservable();
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

  it('sorts recent workflows most-recently-created first', () => {
    fixture.detectChanges();
    fakeService.workflows$.next([
      workflow({ id: 'wf-old', createdAt: '2026-01-01T00:00:00Z' }),
      workflow({ id: 'wf-new', createdAt: '2026-01-02T00:00:00Z' })
    ]);
    fixture.detectChanges();

    expect(component.sortedWorkflows().map((w) => w.id)).toEqual(['wf-new', 'wf-old']);
  });

  it('toggles a workflow row expanded and collapsed, keyed by clientPromptId', () => {
    fixture.detectChanges();
    fakeService.workflows$.next([workflow({ id: 'wf-1', clientPromptId: 'prompt-1' })]);
    fixture.detectChanges();

    expect(component.isExpanded('prompt-1')).toBeFalse();
    component.toggle('prompt-1');
    expect(component.isExpanded('prompt-1')).toBeTrue();
    component.toggle('prompt-1');
    expect(component.isExpanded('prompt-1')).toBeFalse();
  });

  it('auto-expands the just-submitted prompt id immediately, once', () => {
    fixture.componentRef.setInput('justSubmittedPromptId', 'prompt-1');
    fixture.detectChanges();

    expect(component.isExpanded('prompt-1')).toBeTrue();

    component.toggle('prompt-1');
    fixture.detectChanges();

    expect(component.isExpanded('prompt-1')).toBeFalse();
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

    fakeService.workflows$.next([workflow({ id: 'wf-real', clientPromptId: 'prompt-pending' })]);
    fixture.detectChanges();

    const matches = component
      .sortedWorkflows()
      .filter((w) => w.clientPromptId === 'prompt-pending');
    expect(matches.length).toBe(1);
    expect(matches[0].id).toBe('wf-real');
  });
});
