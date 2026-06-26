import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { DepositProductsListComponent } from './deposit-products-list';
import { ProductService, DepositProduct } from '../product.service';

type Svc = Record<'listDepositProducts', ReturnType<typeof vi.fn>>;

function product(over: Partial<DepositProduct> = {}): DepositProduct {
  return {
    id: 'd1', name: 'Standard Savings', shortName: 'SAV', description: 'desc',
    accountType: 'SAVINGS', currencyCode: 'USD', minimumBalance: 0,
    interestRate: 2, interestCompounding: 'MONTHLY', interestPostingPeriodType: 'MONTHLY',
    daysInYearType: 'ACTUAL', daysInMonthType: 'ACTUAL',
    withdrawalFeeForTransfers: false, allowOverdraft: false,
    accountingType: 'NONE', charges: [], active: true, ...over,
  };
}

describe('DepositProductsListComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = { listDepositProducts: vi.fn().mockReturnValue(of([product()])) };
    TestBed.configureTestingModule({
      imports: [DepositProductsListComponent],
      providers: [provideRouter([]), { provide: ProductService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(DepositProductsListComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads products on init', () => {
    const c = make();
    expect(svc.listDepositProducts).toHaveBeenCalledWith(false);
    expect(c.products).toHaveLength(1);
    expect(c.filtered).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('clears loading on error', () => {
    svc.listDepositProducts.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.loading).toBe(false);
  });

  it('onActiveFilterChange reloads with the activeOnly flag', () => {
    const c = make();
    c.activeOnly = true;
    c.onActiveFilterChange();
    expect(svc.listDepositProducts).toHaveBeenLastCalledWith(true);
  });

  describe('applyFilter (type + search)', () => {
    let c: DepositProductsListComponent;
    beforeEach(() => {
      c = make();
      c.products = [
        product({ id: 'a', name: 'Alpha Savings', accountType: 'SAVINGS' }),
        product({ id: 'b', name: 'Beta Checking', shortName: 'CHK', accountType: 'CHECKING' }),
        product({ id: 'c', name: 'Gamma Fixed', accountType: 'FIXED_DEPOSIT', description: 'term' }),
      ];
    });

    it('filters by accountType when typeFilter is set', () => {
      c.typeFilter = 'CHECKING';
      c.onTypeFilterChange();
      expect(c.filtered.map(p => p.id)).toEqual(['b']);
    });

    it('combines type filter with the search term', () => {
      c.typeFilter = 'FIXED_DEPOSIT';
      c.applyFilter('term');
      expect(c.filtered.map(p => p.id)).toEqual(['c']);
      c.applyFilter('alpha');
      expect(c.filtered).toHaveLength(0);
    });

    it('matches all types and resets page on empty term', () => {
      c.typeFilter = '';
      c.page = 3;
      c.applyFilter('');
      expect(c.filtered).toHaveLength(3);
      expect(c.page).toBe(0);
    });
  });

  describe('pagination', () => {
    let c: DepositProductsListComponent;
    beforeEach(() => {
      c = make();
      c.filtered = Array.from({ length: 20 }, (_, i) => product({ id: `p${i}` }));
    });

    it('computes window helpers and walks pages within bounds', () => {
      c.page = 0;
      expect(c.totalPages).toBe(2);
      expect(c.startRow).toBe(1);
      expect(c.endRow).toBe(15);
      expect(c.pageItems).toHaveLength(15);
      c.nextPage();
      expect(c.page).toBe(1);
      expect(c.startRow).toBe(16);
      expect(c.endRow).toBe(20);
      c.nextPage(); expect(c.page).toBe(1);
      c.prevPage(); expect(c.page).toBe(0);
      c.prevPage(); expect(c.page).toBe(0);
    });

    it('startRow is 0 with no rows', () => {
      c.filtered = [];
      expect(c.startRow).toBe(0);
    });
  });

  describe('label helpers', () => {
    let c: DepositProductsListComponent;
    beforeEach(() => { c = make(); });

    it('accountTypeLabel maps and falls back', () => {
      expect(c.accountTypeLabel('FIXED_DEPOSIT')).toBe('Fixed Deposit');
      expect(c.accountTypeLabel('OTHER')).toBe('OTHER');
    });

    it('compoundingLabel maps and falls back', () => {
      expect(c.compoundingLabel('QUARTERLY')).toBe('Quarterly');
      expect(c.compoundingLabel('NONE')).toBe('NONE');
    });

    it('productVariant reflects active state', () => {
      expect(c.productVariant(product({ active: true }))).toBe('success');
      expect(c.productVariant(product({ active: false }))).toBe('neutral');
    });
  });
});
