import { Task } from '../client-prompt.model';
import { flattenTasks, TaskGraphLayoutBuilder } from './task-graph-layout.builder';

function task(overrides: Partial<Task> & Pick<Task, 'id' | 'title'>): Task {
  return {
    description: 'description',
    subTasks: [],
    dependsOnTaskIds: [],
    ...overrides
  };
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
  it('places a task with no dependencies in the first column', () => {
    const layout = TaskGraphLayoutBuilder.build([task({ id: 't1', title: 'a' })]);

    expect(layout.nodes).toEqual([{ taskId: 't1', title: 'a', x: 110, y: 110 }]);
    expect(layout.edges).toEqual([]);
  });

  it('places a dependent task one column right of its dependency', () => {
    const tasks = [
      task({ id: 'a', title: 'a' }),
      task({ id: 'b', title: 'b', dependsOnTaskIds: ['a'] })
    ];

    const layout = TaskGraphLayoutBuilder.build(tasks);

    const nodeA = layout.nodes.find((n) => n.taskId === 'a')!;
    const nodeB = layout.nodes.find((n) => n.taskId === 'b')!;
    expect(nodeB.x).toBeGreaterThan(nodeA.x);
    expect(layout.edges).toEqual([{ fromTaskId: 'a', toTaskId: 'b' }]);
  });

  it('places a task one column past the deepest of its several dependencies', () => {
    const tasks = [
      task({ id: 'a', title: 'a' }),
      task({ id: 'b', title: 'b', dependsOnTaskIds: ['a'] }),
      task({ id: 'c', title: 'c', dependsOnTaskIds: ['a', 'b'] })
    ];

    const layout = TaskGraphLayoutBuilder.build(tasks);

    const columnOf = (id: string) => layout.nodes.find((n) => n.taskId === id)!.x;
    expect(columnOf('c')).toBeGreaterThan(columnOf('b'));
    expect(columnOf('b')).toBeGreaterThan(columnOf('a'));
  });

  it('stacks tasks sharing a column at different rows', () => {
    const tasks = [task({ id: 'a', title: 'a' }), task({ id: 'b', title: 'b' })];

    const layout = TaskGraphLayoutBuilder.build(tasks);

    const rows = layout.nodes.map((n) => n.y);
    expect(new Set(rows).size).toBe(2);
  });

  it('ignores a dependsOnTaskIds reference to an unknown task id', () => {
    const layout = TaskGraphLayoutBuilder.build([
      task({ id: 'a', title: 'a', dependsOnTaskIds: ['does-not-exist'] })
    ]);

    expect(layout.nodes[0].x).toBe(110);
    expect(layout.edges).toEqual([]);
  });

  it('sizes the viewBox to fit every node', () => {
    const tasks = [
      task({ id: 'a', title: 'a' }),
      task({ id: 'b', title: 'b', dependsOnTaskIds: ['a'] })
    ];

    const layout = TaskGraphLayoutBuilder.build(tasks);

    expect(layout.width).toBeGreaterThan(220);
    expect(layout.height).toBeGreaterThan(0);
  });
});
