/** One icon in a nav rail. `icon` selects which glyph `NavRailComponent` draws; add a case there
 *  when adding a new value here. */
export type NavIcon = 'workflows' | 'prompt' | 'system' | 'queues';

export interface NavItem {
  id: string;
  label: string;
  icon: NavIcon;
}
