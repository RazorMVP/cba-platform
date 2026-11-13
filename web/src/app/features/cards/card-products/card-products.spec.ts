import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { CardProductsComponent } from './card-products';
import { CardsService, CardProduct } from '../cards.service';

type Svc = Record<'listProducts' | 'createProduct' | 'updateProduct', ReturnType<typeof vi.fn>>;

function product(over: Partial<CardProduct> = {}): CardProduct {
  return {
    id: 'p1', name: 'Classic Debit', cardType: 'DEBIT',
    binRangeStart: '45731200', binRangeEnd: '45731299', defaultDailyLimit: 500000,
    features: {}, ...over,
  };
}

describe('CardProductsComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listProducts: vi.fn().mockReturnValue(of([product()])),
      createProduct: vi.fn().mockReturnValue(of(product({ id: 'p2', name: 'Credit' }))),
      updateProduct: vi.fn().mockReturnValue(of(product())),
    };
    TestBed.configureTestingModule({
      imports: [CardProductsComponent],
      providers: [provideRouter([]), { provide: CardsService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(CardProductsComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads products on init', () => {
    const c = make();
    expect(svc.listProducts).toHaveBeenCalled();
    expect(c.products).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('keeps loading false on error', () => {
    svc.listProducts.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.loading).toBe(false);
  });

  it('openCreate resets the form to defaults and opens', () => {
    const c = make();
    c.form.name = 'dirty';
    c.openCreate();
    expect(c.showModal).toBe(true);
    expect(c.form).toEqual({ name: '', cardType: 'DEBIT', binRangeStart: '', binRangeEnd: '', defaultDailyLimit: 500000,
      maxWalletBalance: null, dailyWalletLoad: null });
  });

  it('submit creates a product then closes and reloads', () => {
    const c = make();
    c.openCreate();
    c.form = { name: 'Credit', cardType: 'CREDIT', binRangeStart: '5', binRangeEnd: '6', defaultDailyLimit: 100,
      maxWalletBalance: 900 };   // ignored: not a prepaid product
    svc.listProducts.mockClear();
    c.submit();
    expect(svc.createProduct).toHaveBeenCalledWith({ name: 'Credit', cardType: 'CREDIT', binRangeStart: '5', binRangeEnd: '6',
      defaultDailyLimit: 100, maxWalletBalance: null, dailyWalletLoad: null });
    expect(c.showModal).toBe(false);
    expect(svc.listProducts).toHaveBeenCalled();
  });

  it('typeClass maps card type to chip class', () => {
    const c = make();
    expect(c.typeClass('DEBIT')).toBe('chip-info');
    expect(c.typeClass('PREPAID')).toBe('chip-warn');
    expect(c.typeClass('CREDIT')).toBe('chip-primary');
  });

  describe('wallet limits (backlog 15c)', () => {
    it('shows the wallet limit fields only for a prepaid product', () => {
      const fixture = TestBed.createComponent(CardProductsComponent);
      fixture.detectChanges();
      const c = fixture.componentInstance;
      c.openCreate();
      fixture.detectChanges();
      const el = fixture.nativeElement as HTMLElement;
      expect(el.querySelector('[data-test="wallet-limit-fields"]')).toBeNull();
      c.form.cardType = 'PREPAID';
      fixture.detectChanges();
      expect(el.querySelector('[data-test="wallet-limit-fields"]')).not.toBeNull();
    });

    it('creates a prepaid product with its limits; a blank or zero field means the currency default', () => {
      const c = make();
      c.openCreate();
      c.form = { name: 'Full KYC', cardType: 'PREPAID', binRangeStart: '43', binRangeEnd: '44', defaultDailyLimit: 1,
        maxWalletBalance: 5000000, dailyWalletLoad: 0 };
      c.submit();
      expect(svc.createProduct).toHaveBeenCalledWith(expect.objectContaining({ maxWalletBalance: 5000000, dailyWalletLoad: null }));
    });

    it('edit PUTs the product with the new name, daily limit and wallet limits', () => {
      const prepaid = product({ id: 'pp', name: 'Prepaid', cardType: 'PREPAID', maxWalletBalance: null, dailyWalletLoad: 1000 });
      const c = make();
      c.openEdit(prepaid);
      expect(c.editing).toBe(prepaid);
      expect(c.form.dailyWalletLoad).toBe(1000);
      c.form.name = 'Prepaid Plus';
      c.form.maxWalletBalance = 2000000;
      svc.listProducts.mockClear();
      c.submit();
      expect(svc.updateProduct).toHaveBeenCalledWith('pp', expect.objectContaining({ id: 'pp', name: 'Prepaid Plus',
        cardType: 'PREPAID', maxWalletBalance: 2000000, dailyWalletLoad: 1000 }));
      expect(c.showModal).toBe(false);
      expect(c.editing).toBeNull();
      expect(svc.listProducts).toHaveBeenCalled();
    });

    it('a refusal keeps the modal open with the server message', () => {
      svc.updateProduct.mockReturnValue(throwError(() => ({ error: { errors: [{ message: 'Wallet limits must be positive' }] } })));
      const c = make();
      c.openEdit(product({ cardType: 'PREPAID' }));
      c.submit();
      expect(c.showModal).toBe(true);
      expect(c.saving).toBe(false);
      expect(c.error).toContain('must be positive');
    });

    it('walletLimits summarises the product', () => {
      const c = make();
      expect(c.walletLimits(product())).toBe('—');
      expect(c.walletLimits(product({ cardType: 'PREPAID' }))).toBe('Currency default');
      expect(c.walletLimits(product({ cardType: 'PREPAID', maxWalletBalance: 1000000, dailyWalletLoad: null })))
        .toBe('1,000,000 max · default a day');
    });
  });
});
