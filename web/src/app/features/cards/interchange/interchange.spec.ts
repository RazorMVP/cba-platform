import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { InterchangeComponent } from './interchange';
import { CardsService, InterchangeRate, SchemeFee } from '../cards.service';

type Svc = Record<
  'listRates' | 'listSchemeFees' | 'createRate' | 'updateRate' | 'deleteRate',
  ReturnType<typeof vi.fn>
>;

function rate(over: Partial<InterchangeRate> = {}): InterchangeRate {
  return {
    id: 'r1', scheme: 'VISA', cardType: 'DEBIT', mccCategory: null,
    transactionType: 'PURCHASE', channel: 'CARD_PRESENT', ratePercent: 1.5, fixedFee: 0,
    currencyCode: 'USD', effectiveFrom: '2026-01-01', effectiveTo: null, active: true, ...over,
  };
}
function fee(over: Partial<SchemeFee> = {}): SchemeFee {
  return { id: 'f1', scheme: 'VISA', feeType: 'ASSESSMENT', ratePercent: 0.13, fixedFee: 0, effectiveFrom: '2026-01-01', active: true, ...over };
}

describe('InterchangeComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listRates: vi.fn().mockReturnValue(of([rate()])),
      listSchemeFees: vi.fn().mockReturnValue(of([fee()])),
      createRate: vi.fn().mockReturnValue(of(rate({ id: 'r2' }))),
      updateRate: vi.fn().mockReturnValue(of(rate({ id: 'r1', ratePercent: 2 }))),
      deleteRate: vi.fn().mockReturnValue(of(void 0)),
    };
    TestBed.configureTestingModule({
      imports: [InterchangeComponent],
      providers: [provideRouter([]), { provide: CardsService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(InterchangeComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads rates and scheme fees on init', () => {
    const c = make();
    expect(svc.listRates).toHaveBeenCalled();
    expect(svc.listSchemeFees).toHaveBeenCalled();
    expect(c.rates).toHaveLength(1);
    expect(c.fees).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('keeps loading false when rate load fails', () => {
    svc.listRates.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.loading).toBe(false);
  });

  describe('filteredRates', () => {
    it('returns all rates when no scheme filter is set', () => {
      const c = make();
      c.rates = [rate({ id: 'a', scheme: 'VISA' }), rate({ id: 'b', scheme: 'MASTERCARD' })];
      expect(c.filteredRates).toHaveLength(2);
    });
    it('narrows to the selected scheme', () => {
      const c = make();
      c.rates = [rate({ id: 'a', scheme: 'VISA' }), rate({ id: 'b', scheme: 'MASTERCARD' })];
      c.schemeFilter = 'MASTERCARD';
      expect(c.filteredRates.map(r => r.id)).toEqual(['b']);
    });
  });

  it('openCreate resets the form and edit id', () => {
    const c = make();
    c.editId = 'r1';
    c.openCreate();
    expect(c.editId).toBeNull();
    expect(c.showModal).toBe(true);
    expect(c.form.scheme).toBe('VISA');
    expect(c.form.ratePercent).toBe(1.5);
  });

  it('openEdit seeds the form, mapping nulls to undefined', () => {
    const c = make();
    c.openEdit(rate({ id: 'r1', mccCategory: null, effectiveTo: null, scheme: 'VERVE' }));
    expect(c.editId).toBe('r1');
    expect(c.form.scheme).toBe('VERVE');
    expect(c.form.mccCategory).toBeUndefined();
    expect(c.form.effectiveTo).toBeUndefined();
    expect(c.showModal).toBe(true);
  });

  describe('submit', () => {
    it('creates without an edit id', () => {
      const c = make();
      c.openCreate();
      svc.listRates.mockClear();
      c.submit();
      expect(svc.createRate).toHaveBeenCalledWith(c.form);
      expect(c.showModal).toBe(false);
      expect(svc.listRates).toHaveBeenCalled();
    });
    it('updates with an edit id', () => {
      const c = make();
      c.openEdit(rate({ id: 'r1' }));
      c.submit();
      expect(svc.updateRate).toHaveBeenCalledWith('r1', c.form);
      expect(svc.createRate).not.toHaveBeenCalled();
    });
  });

  it('deleteRate removes by reloading', () => {
    const c = make();
    svc.listRates.mockClear();
    c.deleteRate('r1');
    expect(svc.deleteRate).toHaveBeenCalledWith('r1');
    expect(svc.listRates).toHaveBeenCalled();
  });

  it('totalFeePercent adds the fixed fee as a percentage of the amount', () => {
    const c = make();
    // 1.5% + (5 / 1000 * 100) = 1.5 + 0.5 = 2.0
    expect(c.totalFeePercent(1000, rate({ ratePercent: 1.5, fixedFee: 5 }))).toBeCloseTo(2.0);
  });
});
