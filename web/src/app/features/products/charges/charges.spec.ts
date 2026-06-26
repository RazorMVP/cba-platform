import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ChargesComponent } from './charges';
import { ProductService, ChargeDefinition } from '../product.service';

type Svc = Record<'listCharges' | 'createCharge' | 'updateCharge' | 'deleteCharge', ReturnType<typeof vi.fn>>;

function charge(over: Partial<ChargeDefinition> = {}): ChargeDefinition {
  return {
    id: 'ch1', name: 'Processing Fee', currencyCode: 'USD',
    chargeAppliesTo: 'LOAN', chargeTimeType: 'DISBURSEMENT', chargeCalculation: 'FLAT',
    amount: 25, active: true, penalty: false, createdAt: '2026-01-01', ...over,
  };
}

function pageOf(content: ChargeDefinition[], totalElements = content.length, totalPages = 1) {
  return of({ content, totalElements, totalPages, size: 20, number: 0 });
}

const stop = { stopPropagation: vi.fn() } as unknown as Event;

describe('ChargesComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listCharges: vi.fn().mockReturnValue(pageOf([charge()])),
      createCharge: vi.fn().mockReturnValue(of(charge({ id: 'ch2' }))),
      updateCharge: vi.fn().mockReturnValue(of(charge({ id: 'ch1', name: 'Edited' }))),
      deleteCharge: vi.fn().mockReturnValue(of(void 0)),
    };
    TestBed.configureTestingModule({
      imports: [ChargesComponent],
      providers: [provideRouter([]), { provide: ProductService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(ChargesComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads the first page on init (no appliesTo filter)', () => {
    const c = make();
    expect(svc.listCharges).toHaveBeenCalledWith(0, 20, undefined);
    expect(c.charges).toHaveLength(1);
    expect(c.total).toBe(1);
    expect(c.loading).toBe(false);
  });

  it('clears loading on error', () => {
    svc.listCharges.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.loading).toBe(false);
  });

  it('onFilterChange resets to page 0 and passes the appliesTo filter', () => {
    const c = make();
    c.page = 2;
    c.filterApplies = 'SAVINGS';
    c.onFilterChange();
    expect(c.page).toBe(0);
    expect(svc.listCharges).toHaveBeenLastCalledWith(0, 20, 'SAVINGS');
  });

  describe('pagination', () => {
    let c: ChargesComponent;
    beforeEach(() => {
      // echo a stable total across reloads (next/prevPage re-call load)
      svc.listCharges.mockReturnValue(pageOf([charge()], 50, 3));
      c = make(); // load() sets total=50, totalPages=3
    });

    it('startRow / endRow compute the window', () => {
      c.page = 0;
      expect(c.startRow).toBe(1);
      expect(c.endRow).toBe(20);
      c.page = 2;
      expect(c.startRow).toBe(41);
      expect(c.endRow).toBe(50);
    });

    it('startRow is 0 with no rows', () => {
      c.total = 0;
      expect(c.startRow).toBe(0);
    });

    it('nextPage advances and reloads, never past the last page', () => {
      c.page = 0;
      c.nextPage(); expect(c.page).toBe(1);
      c.nextPage(); expect(c.page).toBe(2);
      c.nextPage(); expect(c.page).toBe(2);
      expect(svc.listCharges).toHaveBeenLastCalledWith(2, 20, undefined);
    });

    it('prevPage decrements and reloads, never below 0', () => {
      c.page = 1;
      c.prevPage(); expect(c.page).toBe(0);
      c.prevPage(); expect(c.page).toBe(0);
    });
  });

  describe('timeTypeOptions', () => {
    it('is empty until applies-to is chosen, then scopes by applies-to', () => {
      const c = make();
      expect(c.timeTypeOptions).toEqual([]);
      c.form.chargeAppliesTo = 'LOAN';
      expect(c.timeTypeOptions).toContain('DISBURSEMENT');
      c.form.chargeAppliesTo = 'SAVINGS';
      expect(c.timeTypeOptions).toContain('WITHDRAWAL_FEE');
    });
  });

  describe('create / edit modal', () => {
    it('openCreate resets the form to blank defaults', () => {
      const c = make();
      c.openCreate();
      expect(c.showModal).toBe(true);
      expect(c.editTarget).toBeNull();
      expect(c.form.name).toBe('');
      expect(c.form.chargeCalculation).toBe('FLAT');
      expect(c.form.active).toBe(true);
    });

    it('openEdit copies the charge into the form without mutating the source', () => {
      const c = make();
      const src = charge({ id: 'ch1', name: 'Orig', amount: 99 });
      c.openEdit(src, stop);
      expect(c.showModal).toBe(true);
      expect(c.editTarget).toBe(src);
      expect(c.form.name).toBe('Orig');
      c.form.name = 'Mutated';
      expect(src.name).toBe('Orig');
    });

    it('onAppliesToChange clears the time type', () => {
      const c = make();
      c.form.chargeTimeType = 'DISBURSEMENT';
      c.onAppliesToChange();
      expect(c.form.chargeTimeType).toBe('');
    });
  });

  describe('save', () => {
    it('blocks when required fields are missing', () => {
      const c = make();
      c.openCreate(); // chargeAppliesTo='' → invalid
      c.save();
      expect(c.saveError).toBe('All fields are required.');
      expect(svc.createCharge).not.toHaveBeenCalled();
    });

    it('creates a charge from a valid form and reloads', () => {
      const c = make();
      c.openCreate();
      c.form = {
        name: ' New Fee ', currencyCode: 'USD', chargeAppliesTo: 'LOAN',
        chargeTimeType: 'DISBURSEMENT', chargeCalculation: 'FLAT',
        amount: 30, penalty: false, active: true,
      };
      c.save();
      expect(svc.createCharge).toHaveBeenCalledWith(expect.objectContaining({ name: 'New Fee', amount: 30 }));
      expect(c.showModal).toBe(false);
      expect(c.saving).toBe(false);
    });

    it('updates the edit target when present', () => {
      const c = make();
      c.openEdit(charge({ id: 'ch1' }), stop);
      c.form.amount = 50;
      c.save();
      expect(svc.updateCharge).toHaveBeenCalledWith('ch1', expect.objectContaining({ amount: 50 }));
      expect(c.showModal).toBe(false);
    });

    it('surfaces an error on failure and keeps the modal open', () => {
      svc.createCharge.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.openCreate();
      c.form = {
        name: 'Fee', currencyCode: 'USD', chargeAppliesTo: 'LOAN',
        chargeTimeType: 'DISBURSEMENT', chargeCalculation: 'FLAT',
        amount: 1, penalty: false, active: true,
      };
      c.save();
      expect(c.saveError).toBe('Save failed. Please try again.');
      expect(c.saving).toBe(false);
    });
  });

  describe('delete', () => {
    it('openDelete stages the target then confirmDelete removes it', () => {
      const c = make();
      const target = charge({ id: 'ch9' });
      c.openDelete(target, stop);
      expect(c.deleteTarget).toBe(target);
      expect(c.showDeleteModal).toBe(true);
      c.confirmDelete();
      expect(svc.deleteCharge).toHaveBeenCalledWith('ch9');
      expect(c.showDeleteModal).toBe(false);
      expect(c.deleteTarget).toBeNull();
    });

    it('confirmDelete is a no-op without a target', () => {
      const c = make();
      c.deleteTarget = null;
      c.confirmDelete();
      expect(svc.deleteCharge).not.toHaveBeenCalled();
    });
  });

  describe('label + amount helpers', () => {
    let c: ChargesComponent;
    beforeEach(() => { c = make(); });

    it('appliesToLabel / timeTypeLabel / calculationLabel map and fall back', () => {
      expect(c.appliesToLabel('SAVINGS')).toBe('Savings');
      expect(c.appliesToLabel('XYZ')).toBe('XYZ');
      expect(c.timeTypeLabel('OVERDUE_INSTALLMENT')).toBe('Overdue Installment');
      expect(c.timeTypeLabel('ZZZ')).toBe('ZZZ');
      expect(c.calculationLabel('PERCENT_OF_AMOUNT')).toBe('% of Amount');
      expect(c.calculationLabel('ZZZ')).toBe('ZZZ');
    });

    it('amountDisplay formats flat amounts with currency and percentages otherwise', () => {
      expect(c.amountDisplay(charge({ chargeCalculation: 'FLAT', amount: 1500, currencyCode: 'USD' })))
        .toBe('1,500.00 USD');
      expect(c.amountDisplay(charge({ chargeCalculation: 'PERCENT_OF_AMOUNT', amount: 2.5 })))
        .toBe('2.50%');
    });
  });
});
