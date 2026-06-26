import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { SidebarComponent } from './sidebar';

describe('SidebarComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [SidebarComponent],
      providers: [provideRouter([])],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(SidebarComponent);
    fixture.detectChanges(); // full-template render
    return fixture.componentInstance;
  }

  it('renders without error and exposes the nav groups', () => {
    const c = make();
    expect(c.navGroups.length).toBeGreaterThan(0);
    // Operations is the seed-expanded group
    expect(c.navGroups[0].label).toBe('Operations');
  });

  it('marks only the documented items as exact (Dashboard + Card List)', () => {
    const c = make();
    const exactItems = c.navGroups
      .flatMap(g => g.items)
      .filter(i => i.exact)
      .map(i => i.label);
    expect(exactItems).toEqual(['Dashboard', 'Card List']);
  });

  it('every nav item has a route', () => {
    const c = make();
    const items = c.navGroups.flatMap(g => g.items);
    expect(items.length).toBeGreaterThan(0);
    expect(items.every(i => !!i.route)).toBe(true);
  });

  it('seeds Operations as the only expanded group', () => {
    const c = make();
    expect(c.isGroupExpanded('Operations')).toBe(true);
    expect(c.isGroupExpanded('Products')).toBe(false);
  });

  describe('toggleGroup', () => {
    it('expands a collapsed group', () => {
      const c = make();
      c.toggleGroup('Products');
      expect(c.isGroupExpanded('Products')).toBe(true);
    });

    it('collapses an expanded group', () => {
      const c = make();
      c.toggleGroup('Operations');
      expect(c.isGroupExpanded('Operations')).toBe(false);
    });
  });

  it('emits sidebarToggle output type', () => {
    const c = make();
    const spy = vi.fn();
    c.sidebarToggle.subscribe(spy);
    c.sidebarToggle.emit();
    expect(spy).toHaveBeenCalled();
  });
});
