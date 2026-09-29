import { Task } from '../client-prompt.model';
import { TaskGraphLayout } from './task-graph-layout.model';
import { flattenTasks, TaskGraphLayoutBuilder } from './task-graph-layout.builder';

function task(overrides: Partial<Task> & Pick<Task, 'id' | 'title'>): Task {
  return {
    description: 'description',
    subTasks: [],
    dependsOnTaskIds: [],
    ...overrides
  };
}

function nodeOf(layout: TaskGraphLayout, id: string) {
  const node = layout.nodes.find((n) => n.taskId === id);
  if (!node) throw new Error(`no node for ${id}`);
  return node;
}

/** The plan from the incident: upload -> clean -> analyze -> report, report being final. */
function linearPlan(cleanSubTasks: Task[] = []): Task[] {
  return [
    task({ id: 'upload', title: 'Upload' }),
    task({ id: 'clean', title: 'Clean', dependsOnTaskIds: ['upload'], subTasks: cleanSubTasks }),
    task({ id: 'analyze', title: 'Analyze', dependsOnTaskIds: ['clean'] }),
    task({ id: 'report', title: 'Report', dependsOnTaskIds: ['analyze'] })
  ];
}

describe('flattenTasks', () => {
  it('returns root tasks with no subtasks as-is', () => {
    const tasks = [task({ id: 't1', title: 'a' }), task({ id: 't2', title: 'b' })];

    expect(flattenTasks(tasks)).toEqual(tasks);
  });

  it('flattens nested subTasks into one list, parents before children', () => {
    const child = task({ id: 'child', title: 'child' });
    const root = task({ id: 'root', title: 'root', subTasks: [child] });

    expect(flattenTasks([root]).map((t) => t.id)).toEqual(['root', 'child']);
  });
});

describe('TaskGraphLayoutBuilder', () => {
  it('places a lone task at the top-left margin', () => {
    const layout = TaskGraphLayoutBuilder.build([task({ id: 't1', title: 'a' })]);

    expect(layout.nodes).toEqual([{ taskId: 't1', title: 'a', x: 110, y: 110 }]);
    expect(layout.edges).toEqual([]);
  });

  it('puts the final task at the top and each prerequisite below what needs it', () => {
    const layout = TaskGraphLayoutBuilder.build(linearPlan());

    const y = (id: string) => nodeOf(layout, id).y;
    expect(y('report')).toBeLessThan(y('analyze'));
    expect(y('analyze')).toBeLessThan(y('clean'));
    expect(y('clean')).toBeLessThan(y('upload'));
  });

  it('draws dependency edges from the prerequisite up to its dependent', () => {
    const tasks = [
      task({ id: 'a', title: 'a' }),
      task({ id: 'b', title: 'b', dependsOnTaskIds: ['a'] })
    ];

    const layout = TaskGraphLayoutBuilder.build(tasks);

    expect(layout.edges).toEqual([{ fromTaskId: 'a', toTaskId: 'b' }]);
    expect(nodeOf(layout, 'a').y).toBeGreaterThan(nodeOf(layout, 'b').y);
  });

  it('places a shared prerequisite below the lowest of its dependents', () => {
    const tasks = [
      task({ id: 'base', title: 'base' }),
      task({ id: 'mid', title: 'mid', dependsOnTaskIds: ['base'] }),
      task({ id: 'top', title: 'top', dependsOnTaskIds: ['base', 'mid'] })
    ];

    const layout = TaskGraphLayoutBuilder.build(tasks);

    const y = (id: string) => nodeOf(layout, id).y;
    expect(y('base')).toBeGreaterThan(y('mid'));
    expect(y('mid')).toBeGreaterThan(y('top'));
  });

  it('sets tasks sharing a row side by side', () => {
    const tasks = [task({ id: 'a', title: 'a' }), task({ id: 'b', title: 'b' })];

    const layout = TaskGraphLayoutBuilder.build(tasks);

    expect(nodeOf(layout, 'a').y).toBe(nodeOf(layout, 'b').y);
    expect(nodeOf(layout, 'a').x).toBeLessThan(nodeOf(layout, 'b').x);
  });

  it('ignores a dependsOnTaskIds reference to an unknown task id', () => {
    const layout = TaskGraphLayoutBuilder.build([
      task({ id: 'a', title: 'a', dependsOnTaskIds: ['does-not-exist'] })
    ]);

    expect(layout.nodes[0].y).toBe(110);
    expect(layout.edges).toEqual([]);
  });

  describe('with decomposed subtasks', () => {
    const subtasks = () => [
      task({ id: 'fix', title: 'Run the fix', spawnedByAttemptId: 'clean-a1' }),
      task({
        id: 'verify',
        title: 'Verify',
        dependsOnTaskIds: ['fix'],
        spawnedByAttemptId: 'clean-a1'
      })
    ];

    it('renders subtasks below the whole plan without moving the plan', () => {
      const before = TaskGraphLayoutBuilder.build(flattenTasks(linearPlan()));
      const after = TaskGraphLayoutBuilder.build(flattenTasks(linearPlan(subtasks())));

      const lowestPlanRow = Math.max(...before.nodes.map((n) => n.y));
      expect(nodeOf(after, 'fix').y).toBeGreaterThan(lowestPlanRow);
      expect(nodeOf(after, 'verify').y).toBeGreaterThan(lowestPlanRow);
      ['upload', 'clean', 'analyze', 'report'].forEach((id) =>
        expect(nodeOf(after, id).y).toBe(nodeOf(before, id).y)
      );
    });

    it('lays a subtask chain out bottom to top, first step lowest', () => {
      const layout = TaskGraphLayoutBuilder.build(flattenTasks(linearPlan(subtasks())));

      expect(nodeOf(layout, 'fix').y).toBeGreaterThan(nodeOf(layout, 'verify').y);
    });

    it('draws only dependency edges, none from a subtask to its parent', () => {
      const layout = TaskGraphLayoutBuilder.build(flattenTasks(linearPlan(subtasks())));

      expect(layout.edges).not.toContain(jasmine.objectContaining({ toTaskId: 'clean', fromTaskId: 'fix' }));
      expect(layout.edges).not.toContain(
        jasmine.objectContaining({ toTaskId: 'clean', fromTaskId: 'verify' })
      );
      expect(layout.edges).toContain({ fromTaskId: 'fix', toTaskId: 'verify' });
    });

    it('puts a subtask of a subtask in a further band below', () => {
      const grandchild = task({ id: 'deep', title: 'Deep', spawnedByAttemptId: 'fix-a1' });
      const children = [
        task({ id: 'fix', title: 'Run the fix', subTasks: [grandchild] }),
        task({ id: 'verify', title: 'Verify', dependsOnTaskIds: ['fix'] })
      ];

      const layout = TaskGraphLayoutBuilder.build(flattenTasks(linearPlan(children)));

      expect(nodeOf(layout, 'deep').y).toBeGreaterThan(nodeOf(layout, 'fix').y);
    });
  });

  it('sizes the viewBox to fit every node', () => {
    const layout = TaskGraphLayoutBuilder.build(
      flattenTasks([
        task({ id: 'a', title: 'a' }),
        task({ id: 'b', title: 'b' }),
        task({ id: 'c', title: 'c', dependsOnTaskIds: ['a'] })
      ])
    );

    layout.nodes.forEach((node) => {
      expect(node.x).toBeLessThan(layout.width);
      expect(node.y).toBeLessThan(layout.height);
    });
  });
});
