import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NavItem } from './nav-item.model';
import { NavRailComponent } from './nav-rail.component';

const ITEMS: NavItem[] = [{ id: 'workflows', label: 'Workflows', icon: 'workflows' }];

describe('NavRailComponent', () => {
  let fixture: ComponentFixture<NavRailComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [NavRailComponent] });
    fixture = TestBed.createComponent(NavRailComponent);
    fixture.componentRef.setInput('items', ITEMS);
  });

  it('marks no item active when activeId is null', () => {
    fixture.componentRef.setInput('activeId', null);
    fixture.detectChanges();

    expect(fixture.componentInstance.isActive('workflows')).toBeFalse();
  });

  it('marks the matching item active', () => {
    fixture.componentRef.setInput('activeId', 'workflows');
    fixture.detectChanges();

    expect(fixture.componentInstance.isActive('workflows')).toBeTrue();
  });

  it('emits the clicked item id', () => {
    fixture.componentRef.setInput('activeId', null);
    fixture.detectChanges();
    let emitted: string | undefined;
    fixture.componentInstance.itemClicked.subscribe((id) => (emitted = id));

    const button = (fixture.nativeElement as HTMLElement).querySelector('.nav-item') as HTMLButtonElement;
    button.click();

    expect(emitted).toBe('workflows');
  });
});
