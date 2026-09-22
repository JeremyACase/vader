import { Component, input, output } from '@angular/core';
import { NavItem } from './nav-item.model';

/**
 * Left-anchored vertical icon rail (a la an IDE's activity bar). Purely presentational: the
 * parent owns which item is active and what showing it means — clicking an item just emits its
 * id, whether that toggles a panel open/closed is the parent's call.
 */
@Component({
  selector: 'app-nav-rail',
  standalone: true,
  imports: [],
  templateUrl: './nav-rail.component.html',
  styleUrl: './nav-rail.component.css'
})
export class NavRailComponent {
  items = input.required<NavItem[]>();
  activeId = input<string | null>(null);
  itemClicked = output<string>();

  isActive(id: string): boolean {
    return this.activeId() === id;
  }
}
