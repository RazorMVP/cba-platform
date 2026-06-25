import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { BlacklistComponent } from './blacklist';
import { AdminService, BlacklistEntry } from './admin.service';

type Svc = Record<
  'listBlacklist' | 'searchBlacklist' | 'addBlacklistEntry' | 'updateBlacklistEntry' | 'deactivateBlacklistEntry',
  ReturnType<typeof vi.fn>
>;

function entry(over: Partial<BlacklistEntry> = {}): BlacklistEntry {
  return {
    id: 'bl1', entityType: 'CUSTOMER', entityValue: 'cu1', reason: 'fraud',
    source: 'INTERNAL', active: true, createdAt: '2026-01-01', ...over,
  };
}
function page(content: BlacklistEntry[], totalElements = content.length) {
  return of({ content, totalElements, totalPages: 1, size: 20, number: 0 });
}

describe('BlacklistComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listBlacklist: vi.fn().mockReturnValue(page([entry()], 1)),
      searchBlacklist: vi.fn().mockReturnValue(of([entry({ id: 'bl2' })])),
      addBlacklistEntry: vi.fn().mockReturnValue(of(entry({ id: 'bl3' }))),
      updateBlacklistEntry: vi.fn().mockReturnValue(of(entry())),
      deactivateBlacklistEntry: vi.fn().mockReturnValue(of(entry({ active: false }))),
    };
    TestBed.configureTestingModule({
      imports: [BlacklistComponent],
      providers: [provideRouter([]), { provide: AdminService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(BlacklistComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads entries on init', () => {
    const c = make();
    expect(svc.listBlacklist).toHaveBeenCalledWith(undefined, undefined, 0, 20);
    expect(c.entries).toHaveLength(1);
    expect(c.total).toBe(1);
    expect(c.loading).toBe(false);
  });

  it('clears loading on load failure', () => {
    svc.listBlacklist.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.loading).toBe(false);
  });

  it('loadEntries maps the active filter string to a boolean', () => {
    const c = make();
    c.filterEntityType = 'EMAIL';
    c.filterActive = 'false';
    c.loadEntries();
    expect(svc.listBlacklist).toHaveBeenLastCalledWith('EMAIL', false, 0, 20);
  });

  it('applyFilters resets the page', () => {
    const c = make();
    c.page = 3;
    c.applyFilters();
    expect(c.page).toBe(0);
  });

  it('clearFilters empties filters and reloads', () => {
    const c = make();
    c.filterEntityType = 'EMAIL'; c.filterActive = 'true'; c.searchQuery = 'x';
    c.clearFilters();
    expect(c.filterEntityType).toBe('');
    expect(c.filterActive).toBe('');
    expect(c.searchQuery).toBe('');
  });

  describe('onSearch', () => {
    it('debounced search updates entries for queries >= 2 chars', async () => {
      const c = make();
      c.onSearch('john');
      await new Promise(r => setTimeout(r, 350));
      expect(svc.searchBlacklist).toHaveBeenCalledWith('john');
      expect(c.entries.map(e => e.id)).toEqual(['bl2']);
      expect(c.total).toBe(1);
    });
    it('reloads the full list when the query is cleared', () => {
      const c = make();
      const calls = svc.listBlacklist.mock.calls.length;
      c.onSearch('');
      expect(svc.listBlacklist.mock.calls.length).toBe(calls + 1);
    });
    it('does not search for a single character', async () => {
      const c = make();
      c.onSearch('a');
      await new Promise(r => setTimeout(r, 350));
      expect(svc.searchBlacklist).not.toHaveBeenCalled();
    });
  });

  it('confirmAdd posts the form and reloads', () => {
    const c = make();
    c.openAdd();
    c.form.entityValue = 'cu9';
    c.confirmAdd();
    expect(svc.addBlacklistEntry).toHaveBeenCalledWith(expect.objectContaining({ entityValue: 'cu9' }));
    expect(c.showAddModal).toBe(false);
  });

  describe('openEdit / confirmEdit', () => {
    it('primes the edit fields from the entry', () => {
      const c = make();
      c.openEdit(entry({ reason: 'old', expiresAt: '2026-12-31' }));
      expect(c.editReason).toBe('old');
      expect(c.editExpiresAt).toBe('2026-12-31');
      expect(c.showEditModal).toBe(true);
    });
    it('persists the edit', () => {
      const c = make();
      c.openEdit(entry());
      c.editReason = 'updated';
      c.editExpiresAt = '';
      c.confirmEdit();
      expect(svc.updateBlacklistEntry).toHaveBeenCalledWith('bl1', 'updated', undefined);
      expect(c.showEditModal).toBe(false);
    });
    it('confirmEdit is a no-op without a selection', () => {
      const c = make();
      c.selected = null;
      c.confirmEdit();
      expect(svc.updateBlacklistEntry).not.toHaveBeenCalled();
    });
  });

  it('confirmDeactivate deactivates the selected entry', () => {
    const c = make();
    c.openDeactivate(entry());
    c.confirmDeactivate();
    expect(svc.deactivateBlacklistEntry).toHaveBeenCalledWith('bl1');
    expect(c.showDeactivateConfirm).toBe(false);
  });

  describe('pagination', () => {
    let c: BlacklistComponent;
    beforeEach(() => {
      svc.listBlacklist.mockReturnValue(page([], 50));
      c = make();
      c.total = 50;
    });
    it('nextPage stops at the last page', () => {
      c.page = 0; c.nextPage(); expect(c.page).toBe(1);
      c.nextPage(); expect(c.page).toBe(2);
      c.nextPage(); expect(c.page).toBe(2);
    });
    it('prevPage never below 0', () => {
      c.page = 1; c.prevPage(); expect(c.page).toBe(0);
      c.prevPage(); expect(c.page).toBe(0);
    });
    it('totalPages divides by page size', () => {
      expect(c.totalPages()).toBe(3);
    });
  });
});
