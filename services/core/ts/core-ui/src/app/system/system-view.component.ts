import { Component, signal } from '@angular/core';
import { NavItem } from '../nav-rail/nav-item.model';
import { NavRailComponent } from '../nav-rail/nav-rail.component';
import { QueuesViewComponent } from './queues/queues-view.component';

/**
 * The System section: a secondary icon rail of system tools beside whichever tool is open.
 * Add a tool by adding its item to {@link tools} and a case for its id in the template.
 */
@Component({
  selector: 'app-system-view',
  standalone: true,
  imports: [NavRailComponent, QueuesViewComponent],
  templateUrl: './system-view.component.html',
  styleUrl: './system-view.component.css'
})
export class SystemViewComponent {
  readonly tools: NavItem[] = [{ id: 'queues', label: 'Queues', icon: 'queues' }];

  /** Which tool is open, if any. Clicking the open tool again closes it. */
  readonly activeToolId = signal<string | null>(null);

  toggleTool(id: string): void {
    this.activeToolId.update((current) => (current === id ? null : id));
  }
}
