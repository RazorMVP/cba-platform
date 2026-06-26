import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { LoanProductsListComponent } from './loan-products-list';
import { ProductService, LoanProduct } from '../product.service';

type Svc = Record<'listLoanProducts', ReturnType<typeof vi.fn>>;

function product(over: Partial<LoanProduct> = {}): LoanProduct {
  return {
    id: 'lp1', name: 'Standard Loan', shortName: 'STDL', description: 'desc',
    currencyCode: 'USD', minPrincipal: 100, maxPrincipal: 10000,
    minInterestRate: 1, maxInterestRate: 20, defaultInterestRate: 10,
    interestRateFrequencyType: 'PER_YEAR', interestType: 'DECLINING_BALANCE',
    amortizationType: 'EQUAL_INSTALLMENTS', interestCalculationPeriodType: 'SAME_AS_REPAYMENT_PERIOD',
    daysInYearType: 'ACTUAL', daysInMonthType: 'ACTUAL',
    minTermMonths: 1, maxTermMonths: 60, numberOfRepayments: 12, repaymentEvery: 1,
    repaymentFrequencyType: 'MONTHS', repaymentType: 'ANNUITY',
    originationFee: 0, latePaymentFee: 0,
    allowAttributeOverrides: {
      amortizationType: true, interestType: true, repaymentEvery: true, repaymentFrequency: true,
      repaymentStrategy: true, graceOnPrincipalAndInterestPayment: true,
      graceOnInterestCharged: true, interestRatePerPeriod: true,
    },
    charges: [], active: true, ...over,
  };
}

describe('LoanProductsListComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = { listLoanProducts: vi.fn().mockReturnValue(of([product()])) };
    TestBed.configureTestingModule({
      imports: [LoanProductsListComponent],
      providers: [provideRouter([]), { provide: ProductService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(LoanProductsListComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads products on init and seeds the filtered list', () => {
    const c = make();
    expect(svc.listLoanProducts).toHaveBeenCalledWith(false);
    expect(c.products).toHaveLength(1);
    expect(c.filtered).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('clears loading on error', () => {
    svc.listLoanProducts.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.loading).toBe(false);
  });

  it('onActiveFilterChange reloads with the activeOnly flag', () => {
    const c = make();
    c.activeOnly = true;
    c.page = 4;
    c.onActiveFilterChange();
    expect(c.page).toBe(0);
    expect(svc.listLoanProducts).toHaveBeenLastCalledWith(true);
  });

  describe('applyFilter', () => {
    let c: LoanProductsListComponent;
    beforeEach(() => {
      c = make();
      c.products = [
        product({ id: 'a', name: 'Alpha Loan', shortName: 'ALPH' }),
        product({ id: 'b', name: 'Beta Loan', shortName: 'BETA', description: 'special' }),
      ];
    });

    it('matches by name, shortName or description (case-insensitive)', () => {
      c.applyFilter('alph'); expect(c.filtered.map(p => p.id)).toEqual(['a']);
      c.applyFilter('BETA'); expect(c.filtered.map(p => p.id)).toEqual(['b']);
      c.applyFilter('special'); expect(c.filtered.map(p => p.id)).toEqual(['b']);
    });

    it('returns a copy of all products on an empty query and resets page', () => {
      c.page = 2;
      c.applyFilter('');
      expect(c.filtered).toHaveLength(2);
      expect(c.filtered).not.toBe(c.products);
      expect(c.page).toBe(0);
    });
  });

  describe('pagination', () => {
    let c: LoanProductsListComponent;
    beforeEach(() => {
      c = make();
      // 32 items at pageSize 15 → 3 pages (15 / 15 / 2)
      c.filtered = Array.from({ length: 32 }, (_, i) => product({ id: `p${i}` }));
    });

    it('totalPages / startRow / endRow / pageItems compute the window', () => {
      c.page = 0;
      expect(c.totalPages).toBe(3);
      expect(c.startRow).toBe(1);
      expect(c.endRow).toBe(15);
      expect(c.pageItems).toHaveLength(15);
      c.page = 2;
      expect(c.startRow).toBe(31);
      expect(c.endRow).toBe(32);
      expect(c.pageItems).toHaveLength(2);
    });

    it('startRow is 0 when there are no rows', () => {
      c.filtered = [];
      expect(c.startRow).toBe(0);
      expect(c.totalPages).toBe(1);
    });

    it('nextPage / prevPage stay within bounds', () => {
      c.page = 0; c.nextPage(); expect(c.page).toBe(1);
      c.nextPage(); expect(c.page).toBe(2);
      c.nextPage(); expect(c.page).toBe(2);
      c.prevPage(); expect(c.page).toBe(1);
      c.prevPage(); c.prevPage(); expect(c.page).toBe(0);
    });
  });

  describe('label helpers', () => {
    let c: LoanProductsListComponent;
    beforeEach(() => { c = make(); });

    it('repaymentLabel maps known types and falls back', () => {
      expect(c.repaymentLabel('ANNUITY')).toBe('Annuity');
      expect(c.repaymentLabel('DECLINING_BALANCE')).toBe('Declining Balance');
      expect(c.repaymentLabel('WEIRD')).toBe('WEIRD');
    });

    it('interestTypeLabel distinguishes declining vs flat', () => {
      expect(c.interestTypeLabel('DECLINING_BALANCE')).toBe('Declining Bal.');
      expect(c.interestTypeLabel('FLAT')).toBe('Flat');
    });

    it('productVariant reflects active state', () => {
      expect(c.productVariant(product({ active: true }))).toBe('success');
      expect(c.productVariant(product({ active: false }))).toBe('neutral');
    });
  });
});
