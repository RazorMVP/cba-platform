import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { TellerListComponent } from './teller-list';
import { TellerService, Teller } from './teller.service';

type Svc = Record<'list' | 'create', ReturnType<typeof vi.fn>>;

function teller(over: Partial<Teller> = {}): Teller {
  return {
    id: 't1', name: 'Main Desk', branchCode: '001',
    status: 'ACTIVE', startDate: '2026-01-01', ...over,
  };
}

describe('TellerListComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      list: vi.fn().mockReturnValue(of([
        teller(),
        teller({ id: 't2', name: 'Branch Two', branchCode: '002', status: 'INACTIVE' }),
      ])),
      create: vi.fn().mockReturnValue(of(teller({ id: 't3', name: 'New Desk', branchCode: '003' }))),
    };
    TestBed.configureTestingModule({
      imports: [TellerListComponent],
      providers: [provideRouter([]), { provide: TellerService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(TellerListComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads tellers on init and renders without error', () => {
    const c = make();
    expect(svc.list).toHaveBeenCalled();
    expect(c.tellers).toHaveLength(2);
    expect(c.filtered).toHaveLength(2);
    expect(c.loading).toBe(false);
  });

  it('clears the loading flag even when the list errors', () => {
    svc.list.mockReturnValue(throwError(() => new Error('boom')));
    const c = make();
    expect(c.loading).toBe(false);
    expect(c.tellers).toHaveLength(0);
  });

  describe('filtering', () => {
    it('filters by search query across name and branch code', () => {
      const c = make();
      c.searchQuery = 'branch';
      c.applyFilter();
      expect(c.filtered.map(t => t.id)).toEqual(['t2']);

      c.searchQuery = '001';
      c.applyFilter();
      expect(c.filtered.map(t => t.id)).toEqual(['t1']);
    });

    it('filters by status', () => {
      const c = make();
      c.statusFilter = 'INACTIVE';
      c.applyFilter();
      expect(c.filtered.map(t => t.id)).toEqual(['t2']);
    });

    it('an empty filter returns everything', () => {
      const c = make();
      c.searchQuery = '';
      c.statusFilter = '';
      c.applyFilter();
      expect(c.filtered).toHaveLength(2);
    });
  });

  describe('create modal', () => {
    it('openCreateModal seeds a blank form with today as start date', () => {
      const c = make();
      c.openCreateModal();
      expect(c.showCreateModal).toBe(true);
      expect(c.form.name).toBe('');
      expect(c.form.branchCode).toBe('');
      expect(c.form.startDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    });

    it('submitCreate is a no-op when required fields are missing', () => {
      const c = make();
      c.form = { name: '', branchCode: '001', startDate: '2026-01-01' };
      c.submitCreate();
      expect(svc.create).not.toHaveBeenCalled();
    });

    it('submitCreate posts the form, appends the result and closes', () => {
      const c = make();
      c.openCreateModal();
      c.form = { name: 'New Desk', branchCode: '003', startDate: '2026-01-01' };
      c.submitCreate();
      expect(svc.create).toHaveBeenCalledWith(c.form);
      expect(c.tellers.map(t => t.id)).toContain('t3');
      expect(c.filtered.map(t => t.id)).toContain('t3');
      expect(c.showCreateModal).toBe(false);
      expect(c.createWorking).toBe(false);
    });

    it('submitCreate surfaces an error and keeps the modal open on failure', () => {
      svc.create.mockReturnValue(throwError(() => new Error('boom')));
      const c = make();
      c.openCreateModal();
      c.form = { name: 'New Desk', branchCode: '003', startDate: '2026-01-01' };
      c.submitCreate();
      expect(c.createError).toContain('Failed to create teller');
      expect(c.createWorking).toBe(false);
    });

    it('closeCreateModal does nothing while creating', () => {
      const c = make();
      c.showCreateModal = true;
      c.createWorking = true;
      c.closeCreateModal();
      expect(c.showCreateModal).toBe(true);
      c.createWorking = false;
      c.closeCreateModal();
      expect(c.showCreateModal).toBe(false);
    });
  });

  it('statusVariant maps statuses to badge variants', () => {
    const c = make();
    expect(c.statusVariant('ACTIVE')).toBe('success');
    expect(c.statusVariant('INACTIVE')).toBe('warning');
    expect(c.statusVariant('CLOSED')).toBe('neutral');
  });
});
