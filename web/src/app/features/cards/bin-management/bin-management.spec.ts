import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { BinManagementComponent } from './bin-management';
import { CardsService, BinRange } from '../cards.service';

type Svc = Record<'listBins' | 'createBin' | 'updateBin' | 'deleteBin', ReturnType<typeof vi.fn>>;

function bin(over: Partial<BinRange> = {}): BinRange {
  return {
    id: 'bn1', binStart: '45731200', binEnd: '45731299', scheme: 'VISA',
    productType: 'CLASSIC', cardType: 'DEBIT', countryCode: 'US', currencyCode: 'USD',
    active: true, ...over,
  };
}

describe('BinManagementComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listBins: vi.fn().mockReturnValue(of([bin()])),
      createBin: vi.fn().mockReturnValue(of(bin({ id: 'bn2' }))),
      updateBin: vi.fn().mockReturnValue(of(bin({ id: 'bn1', scheme: 'MASTERCARD' }))),
      deleteBin: vi.fn().mockReturnValue(of(void 0)),
    };
    TestBed.configureTestingModule({
      imports: [BinManagementComponent],
      providers: [provideRouter([]), { provide: CardsService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(BinManagementComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads BIN ranges on init', () => {
    const c = make();
    expect(svc.listBins).toHaveBeenCalled();
    expect(c.bins).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('keeps loading false on error', () => {
    svc.listBins.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.loading).toBe(false);
  });

  it('openCreate clears the edit id and resets the form', () => {
    const c = make();
    c.editId = 'bn1';
    c.openCreate();
    expect(c.editId).toBeNull();
    expect(c.showModal).toBe(true);
    expect(c.form.scheme).toBe('VISA');
    expect(c.form.binStart).toBe('');
  });

  it('openEdit seeds the form from the BIN and sets the edit id', () => {
    const c = make();
    c.openEdit(bin({ id: 'bn1', binStart: '5', scheme: 'MASTERCARD', cardType: 'CREDIT' }));
    expect(c.editId).toBe('bn1');
    expect(c.form.binStart).toBe('5');
    expect(c.form.scheme).toBe('MASTERCARD');
    expect(c.form.cardType).toBe('CREDIT');
    expect(c.showModal).toBe(true);
  });

  describe('submit', () => {
    it('creates when no edit id is set', () => {
      const c = make();
      c.openCreate();
      svc.listBins.mockClear();
      c.submit();
      expect(svc.createBin).toHaveBeenCalledWith(c.form);
      expect(svc.updateBin).not.toHaveBeenCalled();
      expect(c.showModal).toBe(false);
      expect(svc.listBins).toHaveBeenCalled();
    });

    it('updates when an edit id is present', () => {
      const c = make();
      c.openEdit(bin({ id: 'bn1' }));
      c.submit();
      expect(svc.updateBin).toHaveBeenCalledWith('bn1', c.form);
      expect(svc.createBin).not.toHaveBeenCalled();
    });
  });

  it('delete removes the BIN by reloading', () => {
    const c = make();
    svc.listBins.mockClear();
    c.delete('bn1');
    expect(svc.deleteBin).toHaveBeenCalledWith('bn1');
    expect(svc.listBins).toHaveBeenCalled();
  });

  it('schemeColor maps schemes to brand colours with a fallback', () => {
    const c = make();
    expect(c.schemeColor('VISA')).toBe('#1a1f71');
    expect(c.schemeColor('MASTERCARD')).toBe('#eb001b');
    expect(c.schemeColor('UNIONPAY')).toBe('#c0102c');
    expect(c.schemeColor('UNKNOWN')).toBe('#374151');
  });
});
