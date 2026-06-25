import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { FloatingRatesComponent } from './floating-rates';
import { SystemService, FloatingRate } from './system.service';

type Svc = Record<
  'listFloatingRates' | 'createFloatingRate' | 'updateFloatingRate' | 'deleteFloatingRate',
  ReturnType<typeof vi.fn>
>;

function rate(over: Partial<FloatingRate> = {}): FloatingRate {
  return {
    id: 'fr1', name: 'Prime', isActive: true, isBaseLendingRate: true,
    createdBy: 'admin', createdOn: '2026-01-01',
    ratePeriods: [{ id: 'p1', fromDate: '2026-01-01', interestRate: 5, isDifferentialToBaseLendingRate: false }],
    ...over,
  };
}

describe('FloatingRatesComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listFloatingRates: vi.fn().mockReturnValue(of([rate()])),
      createFloatingRate: vi.fn().mockReturnValue(of(rate({ id: 'fr2', name: 'New' }))),
      updateFloatingRate: vi.fn().mockReturnValue(of(rate({ id: 'fr1', name: 'Edited' }))),
      deleteFloatingRate: vi.fn().mockReturnValue(of(void 0)),
    };
    TestBed.configureTestingModule({
      imports: [FloatingRatesComponent],
      providers: [provideRouter([]), { provide: SystemService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(FloatingRatesComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads rates on init', () => {
    const c = make();
    expect(svc.listFloatingRates).toHaveBeenCalled();
    expect(c.rates).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('sets an error when loading fails', () => {
    svc.listFloatingRates.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.error).toBe('Failed to load floating rates.');
  });

  it('filtered narrows by name', () => {
    const c = make();
    c.rates = [rate({ id: 'a', name: 'Alpha' }), rate({ id: 'b', name: 'Beta' })];
    c.searchQuery = 'alp';
    expect(c.filtered).toHaveLength(1);
  });

  it('toggleExpand toggles a single id', () => {
    const c = make();
    c.toggleExpand('fr1');
    expect(c.expandedId).toBe('fr1');
    c.toggleExpand('fr1');
    expect(c.expandedId).toBe('');
  });

  describe('openCreate / openEdit seed state', () => {
    it('openCreate clears the target and seeds one blank period', () => {
      const c = make();
      c.openCreate();
      expect(c.editTarget).toBeNull();
      expect(c.activeModal).toBe('create');
      expect(c.periodRows).toHaveLength(1);
    });

    it('openEdit copies name and rate periods', () => {
      const c = make();
      c.openEdit(rate({ id: 'fr1', name: 'Prime' }));
      expect(c.editTarget?.id).toBe('fr1');
      expect(c.form.name).toBe('Prime');
      expect(c.periodRows).toHaveLength(1);
      expect(c.activeModal).toBe('edit');
    });
  });

  describe('period row management', () => {
    it('addPeriod appends, removePeriod keeps at least one', () => {
      const c = make();
      c.openCreate();
      c.addPeriod();
      expect(c.periodRows).toHaveLength(2);
      c.removePeriod(0);
      expect(c.periodRows).toHaveLength(1);
      c.removePeriod(0); // refuses to drop below 1
      expect(c.periodRows).toHaveLength(1);
    });
  });

  describe('submitForm', () => {
    it('does nothing without a name', () => {
      const c = make();
      c.openCreate();
      c.form.name = '';
      c.submitForm();
      expect(svc.createFloatingRate).not.toHaveBeenCalled();
    });

    it('creates a rate and appends it', () => {
      const c = make();
      c.rates = [];
      c.openCreate();
      c.form.name = 'New';
      c.periodRows = [{ fromDate: '2026-01-01', interestRate: 3, isDifferentialToBaseLendingRate: false }];
      c.submitForm();
      expect(svc.createFloatingRate).toHaveBeenCalledWith(expect.objectContaining({ name: 'New' }));
      expect(c.rates).toHaveLength(1);
      expect(c.activeModal).toBeNull();
    });

    it('updates an existing rate in place', () => {
      const c = make();
      c.rates = [rate({ id: 'fr1' })];
      c.openEdit(c.rates[0]);
      c.submitForm();
      expect(svc.updateFloatingRate).toHaveBeenCalledWith('fr1', expect.anything());
      expect(c.rates[0].name).toBe('Edited');
    });

    it('surfaces an error on failure', () => {
      svc.createFloatingRate.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.openCreate();
      c.form.name = 'New';
      c.submitForm();
      expect(c.modalError).toBe('Failed to save.');
      expect(c.modalWorking).toBe(false);
    });
  });

  describe('confirmDelete', () => {
    it('removes the rate on success', () => {
      const c = make();
      c.rates = [rate({ id: 'fr1' })];
      c.openDelete(c.rates[0]);
      c.confirmDelete();
      expect(svc.deleteFloatingRate).toHaveBeenCalledWith('fr1');
      expect(c.rates).toHaveLength(0);
    });

    it('does nothing without a target', () => {
      const c = make();
      c.editTarget = null;
      c.confirmDelete();
      expect(svc.deleteFloatingRate).not.toHaveBeenCalled();
    });

    it('surfaces an error on failure', () => {
      svc.deleteFloatingRate.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.openDelete(rate({ id: 'fr1' }));
      c.confirmDelete();
      expect(c.modalError).toBe('Failed to delete.');
      expect(c.modalWorking).toBe(false);
    });
  });
});
