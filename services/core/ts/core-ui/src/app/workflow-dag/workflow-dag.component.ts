import {
  Component,
  ElementRef,
  afterNextRender,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
  viewChild
} from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { combineLatest, of } from 'rxjs';
import { map, switchMap } from 'rxjs/operators';
import { ActiveWorkflowsService } from '../active-workflows.service';
import { Task, Workflow } from '../client-prompt.model';
import { DagNode } from './task-graph-layout.model';
import { flattenTasks, TaskGraphLayoutBuilder } from './task-graph-layout.builder';
import { TaskNodeStatus, deriveNodeStatus } from './task-node-status';

/** A point in the SVG coordinate space. */
interface Point {
  x: number;
  y: number;
}

/**
 * Renders a workflow's task graph as a DAG: one node per task, colored by its derived status
 * (blue active, green succeeded, red failed, gray never attempted), edges drawn from each task
 * to whatever depends on it. Clicking a node selects it and emits its task id. The graph is
 * pannable (drag) and zoomable (scroll wheel or the toolbar buttons) so large graphs stay legible.
 */
@Component({
  selector: 'app-workflow-dag',
  standalone: true,
  imports: [],
  templateUrl: './workflow-dag.component.html',
  styleUrl: './workflow-dag.component.css'
})
export class WorkflowDagComponent {
  private activeWorkflowsService = inject(ActiveWorkflowsService);
  private dagCanvas = viewChild<ElementRef<HTMLDivElement>>('dagCanvas');

  workflow = input.required<Workflow>();
  taskSelected = output<string>();

  readonly nodeWidth = 160;
  readonly nodeHeight = 56;

  private readonly minScale = 0.25;
  private readonly maxScale = 3;
  private readonly zoomStep = 0.2;
  private readonly dragThreshold = 4;

  scale = signal(1);
  pan = signal<Point>({ x: 0, y: 0 });
  panning = signal(false);

  readonly zoomPercent = computed(() => Math.round(this.scale() * 100));
  readonly transform = computed(() => {
    const p = this.pan();
    return `translate(${p.x}, ${p.y}) scale(${this.scale()})`;
  });

  private readonly workflowId = computed(() => this.workflow().id);

  private hasDragged = false;
  private panPointerId: number | null = null;
  private panStart: Point = { x: 0, y: 0 };
  private panOriginAtStart: Point = { x: 0, y: 0 };
  private pointerDownTaskId: string | null = null;

  constructor() {
    // untracked: resetView() itself reads layout()/dagCanvas(), and effects auto-track every
    // signal read during their run (even nested calls) -- without this, the effect would also
    // depend on layout(), which is a brand-new object on every poll tick, re-centering the view
    // on every poll instead of only when the selected workflow actually changes.
    effect(() => {
      this.workflowId();
      untracked(() => this.resetView());
    });

    // Attached natively (not via an Angular event binding) so preventDefault() reliably
    // suppresses page scroll — Angular/zone.js registers 'wheel' as a passive listener by default.
    afterNextRender(() => {
      this.dagCanvas()?.nativeElement.addEventListener('wheel', (e) => this.onWheel(e), {
        passive: false
      });
    });
  }

  readonly tasks = computed<Task[]>(() =>
    flattenTasks(this.workflow().taskPlan?.taskGraph.tasks ?? [])
  );

  readonly layout = computed(() => TaskGraphLayoutBuilder.build(this.tasks()));

  private nodeById = computed(() => new Map(this.layout().nodes.map((n) => [n.taskId, n])));

  private taskIds = computed(() =>
    this.tasks()
      .map((task) => task.id)
      .filter((id): id is string => !!id)
  );

  private statuses = toSignal(
    toObservable(this.taskIds).pipe(switchMap((ids) => this.statusesFor(ids))),
    { initialValue: new Map<string, TaskNodeStatus>() }
  );

  selectedTaskId = signal<string | null>(null);

  private statusesFor(taskIds: readonly string[]) {
    if (!taskIds.length) {
      return of(new Map<string, TaskNodeStatus>());
    }
    const perTask = taskIds.map((id) =>
      this.activeWorkflowsService
        .taskAttempts(id)
        .pipe(map((attempts) => [id, deriveNodeStatus(attempts)] as const))
    );
    return combineLatest(perTask).pipe(map((entries) => new Map(entries)));
  }

  statusOf(taskId: string): TaskNodeStatus {
    return this.statuses().get(taskId) ?? 'inactive';
  }

  isSelected(taskId: string): boolean {
    return this.selectedTaskId() === taskId;
  }

  select(taskId: string): void {
    this.selectedTaskId.set(taskId);
    this.taskSelected.emit(taskId);
  }

  /** Frames the whole graph: scales it to fit the canvas (never enlarging past 100%) and centers
   *  it, so the full left-to-right layout — and every dependency edge — is visible without the
   *  user having to pan first. Falls back to an untransformed view if the canvas hasn't rendered
   *  yet. */
  resetView(): void {
    const canvas = this.dagCanvas()?.nativeElement;
    const layout = this.layout();
    if (!canvas || !layout.width || !layout.height) {
      this.scale.set(1);
      this.pan.set({ x: 0, y: 0 });
      return;
    }
    const fitScale = Math.min(canvas.clientWidth / layout.width, canvas.clientHeight / layout.height, 1);
    const scale = Math.max(this.minScale, fitScale);
    this.scale.set(scale);
    this.pan.set({
      x: (canvas.clientWidth - layout.width * scale) / 2,
      y: (canvas.clientHeight - layout.height * scale) / 2
    });
  }

  zoomIn(): void {
    this.applyZoom(this.zoomStep);
  }

  zoomOut(): void {
    this.applyZoom(-this.zoomStep);
  }

  onWheel(event: WheelEvent): void {
    event.preventDefault();
    this.applyZoom(event.deltaY > 0 ? -this.zoomStep : this.zoomStep);
  }

  onPanPointerDown(event: PointerEvent): void {
    this.panning.set(true);
    this.hasDragged = false;
    this.panPointerId = event.pointerId;
    this.panStart = { x: event.clientX, y: event.clientY };
    this.panOriginAtStart = this.pan();
    this.pointerDownTaskId = this.taskIdFromEvent(event);
    (event.currentTarget as Element).setPointerCapture(event.pointerId);
  }

  onPanPointerMove(event: PointerEvent): void {
    if (!this.panning() || event.pointerId !== this.panPointerId) return;
    const dx = event.clientX - this.panStart.x;
    const dy = event.clientY - this.panStart.y;
    this.hasDragged = this.hasDragged || Math.hypot(dx, dy) > this.dragThreshold;
    this.pan.set({ x: this.panOriginAtStart.x + dx, y: this.panOriginAtStart.y + dy });
  }

  /** A plain (click) on a node never reaches it: setPointerCapture below retargets the browser's
   *  synthetic click to whatever element captured the pointer (the canvas), not the node under
   *  the cursor. So selection is resolved here instead, from the node id (if any) recorded at
   *  pointerdown, once a genuine tap/click — not a drag — is confirmed on release. */
  onPanPointerUp(event: PointerEvent): void {
    if (event.pointerId !== this.panPointerId) return;
    const taskId = this.pointerDownTaskId;
    const dragged = this.hasDragged;
    this.endPan();
    if (!dragged && taskId) {
      this.select(taskId);
    }
  }

  onPanPointerCancel(): void {
    this.endPan();
  }

  private endPan(): void {
    this.panning.set(false);
    this.panPointerId = null;
    this.pointerDownTaskId = null;
  }

  private taskIdFromEvent(event: PointerEvent): string | null {
    const target = event.target as Element | null;
    return target?.closest('[data-task-id]')?.getAttribute('data-task-id') ?? null;
  }

  private applyZoom(delta: number): void {
    this.scale.update((current) => Math.min(this.maxScale, Math.max(this.minScale, current + delta)));
  }

  nodeOrigin(node: DagNode): Point {
    return { x: node.x - this.nodeWidth / 2, y: node.y - this.nodeHeight / 2 };
  }

  edgeStart(fromTaskId: string): Point {
    const node = this.nodeById().get(fromTaskId);
    return node ? { x: node.x + this.nodeWidth / 2, y: node.y } : { x: 0, y: 0 };
  }

  edgeEnd(toTaskId: string): Point {
    const node = this.nodeById().get(toTaskId);
    return node ? { x: node.x - this.nodeWidth / 2, y: node.y } : { x: 0, y: 0 };
  }
}
