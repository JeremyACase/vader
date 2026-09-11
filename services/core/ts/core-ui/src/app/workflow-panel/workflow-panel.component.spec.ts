import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, Subject } from 'rxjs';
import { ActiveWorkflowsService } from '../active-workflows.service';
import { Workflow } from '../client-prompt.model';
import { TaskAttempt, TaskAttemptTranscript } from '../task-attempt.model';
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

  activeWorkflows(): Observable<Workflow[]> {
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

  beforeEach(() => {
    fakeService = new FakeActiveWorkflowsService();
    TestBed.configureTestingModule({
      imports: [WorkflowPanelComponent],
      providers: [{ provide: ActiveWorkflowsService, useValue: fakeService }]
    });
    fixture = TestBed.createComponent(WorkflowPanelComponent);
    component = fixture.componentInstance;
  });

  it('sorts active workflows most-recently-created first', () => {
    fixture.detectChanges();
    fakeService.workflows$.next([
      workflow({ id: 'wf-old', createdAt: '2026-01-01T00:00:00Z' }),
      workflow({ id: 'wf-new', createdAt: '2026-01-02T00:00:00Z' })
    ]);
    fixture.detectChanges();

    expect(component.sortedWorkflows().map((w) => w.id)).toEqual(['wf-new', 'wf-old']);
  });

  it('toggles a workflow row expanded and collapsed', () => {
    fixture.detectChanges();
    fakeService.workflows$.next([workflow({ id: 'wf-1' })]);
    fixture.detectChanges();

    expect(component.isExpanded('wf-1')).toBeFalse();
    component.toggle('wf-1');
    expect(component.isExpanded('wf-1')).toBeTrue();
    component.toggle('wf-1');
    expect(component.isExpanded('wf-1')).toBeFalse();
  });

  it('auto-expands the workflow matching the just-submitted prompt id, once', () => {
    fixture.componentRef.setInput('justSubmittedPromptId', 'prompt-1');
    fixture.detectChanges();
    fakeService.workflows$.next([workflow({ id: 'wf-1', clientPromptId: 'prompt-1' })]);
    fixture.detectChanges();

    expect(component.isExpanded('wf-1')).toBeTrue();

    component.toggle('wf-1');
    fixture.detectChanges();

    expect(component.isExpanded('wf-1')).toBeFalse();
  });
});
