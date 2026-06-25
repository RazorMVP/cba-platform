import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ExchangeRatesComponent } from './exchange-rates';
import { SystemService, ExchangeRateResponse } from './system.service';

type Svc = Record<
  'listExchangeRates' | 'upsertExchangeRate' | 'deactivateExchangeRate',
  ReturnType<typeof vi.fn>
>;

function rate(over: Partial<ExchangeRateResponse> = {}): ExchangeRateResponse {
  return {
    id: 'r1', fromCurrency: 'USD', toCurrency: 'KES', rate: 135.5, inverseRate: 0.00738,
    active: true, updatedAt: '2026-01-01', ...over,
  };
}

describe('ExchangeRatesComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listExchangeRates: vi.fn().mockReturnValue(of([rate()])),
      upsertExchangeRate: vi.fn().mockReturnValue(of(rate())),
      deactivateExchangeRate: vi.fn().mockReturnValue(of(void 0)),
    };
    TestBed.configureTestingModule({
      imports: [ExchangeRatesComponent],
      providers: [provideRouter([]), { provide: SystemService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(ExchangeRatesComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads rates on init', () => {
    const c = make();
    expect(svc.listExchangeRates).toHaveBeenCalled();
    expect(c.rates).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('sets an error when loading fails', () => {
    svc.listExchangeRates.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.error).toBe('Failed to load exchange rates.');
  });

  describe('openUpsert', () => {
    it('seeds the form from an existing rate', () => {
      const c = make();
      c.openUpsert(rate({ fromCurrency: 'USD', toCurrency: 'GHS', rate: 12 }));
      expect(c.form).toEqual({ fromCurrency: 'USD', toCurrency: 'GHS', rate: 12 });
      expect(c.activeModal).toBe('upsert');
    });

    it('seeds a blank form when called with no rate', () => {
      const c = make();
      c.openUpsert();
      expect(c.form).toEqual({ fromCurrency: '', toCurrency: '', rate: 0 });
    });
  });

  describe('save', () => {
    it('upserts the rate then reloads', () => {
      const c = make();
      c.openUpsert();
      c.form = { fromCurrency: 'USD', toCurrency: 'EUR', rate: 0.9 };
      c.save();
      expect(svc.upsertExchangeRate).toHaveBeenCalledWith(c.form);
      expect(c.activeModal).toBeNull();
      expect(svc.listExchangeRates).toHaveBeenCalledTimes(2);
    });

    it('surfaces an error on failure', () => {
      svc.upsertExchangeRate.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.openUpsert();
      c.save();
      expect(c.modalError).toBe('Save failed. Please try again.');
      expect(c.working).toBe(false);
    });
  });

  describe('confirmDeactivate', () => {
    it('deactivates by currency pair then reloads', () => {
      const c = make();
      c.openDeactivate(rate({ fromCurrency: 'USD', toCurrency: 'KES' }));
      c.confirmDeactivate();
      expect(svc.deactivateExchangeRate).toHaveBeenCalledWith('USD', 'KES');
      expect(svc.listExchangeRates).toHaveBeenCalledTimes(2);
    });

    it('does nothing without a target', () => {
      const c = make();
      c.deactivateTarget = null;
      c.confirmDeactivate();
      expect(svc.deactivateExchangeRate).not.toHaveBeenCalled();
    });
  });

  it('activeRates / inactiveRates partition by the active flag', () => {
    const c = make();
    c.rates = [rate({ id: 'a', active: true }), rate({ id: 'b', active: false })];
    expect(c.activeRates()).toHaveLength(1);
    expect(c.activeRates()[0].id).toBe('a');
    expect(c.inactiveRates()).toHaveLength(1);
    expect(c.inactiveRates()[0].id).toBe('b');
  });
});
