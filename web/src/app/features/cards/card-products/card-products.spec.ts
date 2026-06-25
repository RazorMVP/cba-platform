import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { CardProductsComponent } from './card-products';
import { CardsService, CardProduct } from '../cards.service';

type Svc = Record<'listProducts' | 'createProduct', ReturnType<typeof vi.fn>>;

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
    expect(c.form).toEqual({ name: '', cardType: 'DEBIT', binRangeStart: '', binRangeEnd: '', defaultDailyLimit: 500000 });
  });

  it('submit creates a product then closes and reloads', () => {
    const c = make();
    c.openCreate();
    c.form = { name: 'Credit', cardType: 'CREDIT', binRangeStart: '5', binRangeEnd: '6', defaultDailyLimit: 100 };
    svc.listProducts.mockClear();
    c.submit();
    expect(svc.createProduct).toHaveBeenCalledWith(c.form);
    expect(c.showModal).toBe(false);
    expect(svc.listProducts).toHaveBeenCalled();
  });

  it('typeClass maps card type to chip class', () => {
    const c = make();
    expect(c.typeClass('DEBIT')).toBe('chip-info');
    expect(c.typeClass('PREPAID')).toBe('chip-warn');
    expect(c.typeClass('CREDIT')).toBe('chip-primary');
  });
});
