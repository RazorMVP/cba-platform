import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { FixedDepositsListComponent } from './fixed-deposits-list';
import { ProductService, FixedDepositProduct } from '../product.service';

type Svc = Record<'listFixedDepositProducts', ReturnType<typeof vi.fn>>;

function product(over: Partial<FixedDepositProduct> = {}): FixedDepositProduct {
  return {
    id: 'f1', name: 'Term Deposit', shortName: 'TD12', description: 'desc',
    currencyCode: 'USD', nominalAnnualInterestRate: 5,
    compoundingPeriod: 'MONTHLY', postingPeriod: 'MONTHLY', calculationType: 'DAILY_BALANCE',
    minDepositTerm: 1, minDepositTermType: 'MONTHS',
    prePenaltyApplicable: false, active: true, ...over,
  };
}

describe('FixedDepositsListComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = { listFixedDepositProducts: vi.fn().mockReturnValue(of([product()])) };
    TestBed.configureTestingModule({
      imports: [FixedDepositsListComponent],
      providers: [provideRouter([]), { provide: ProductService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(FixedDepositsListComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads products on init', () => {
    const c = make();
    expect(svc.listFixedDepositProducts).toHaveBeenCalledWith(false);
    expect(c.products).toHaveLength(1);
    expect(c.filtered).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('clears loading on error', () => {
    svc.listFixedDepositProducts.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.loading).toBe(false);
  });

  it('onActiveFilterChange reloads with the activeOnly flag', () => {
    const c = make();
    c.activeOnly = true;
    c.onActiveFilterChange();
    expect(svc.listFixedDepositProducts).toHaveBeenLastCalledWith(true);
  });

  describe('applyFilter', () => {
    let c: FixedDepositsListComponent;
    beforeEach(() => {
      c = make();
      c.products = [
        product({ id: 'a', name: 'Alpha', shortName: 'ALPH' }),
        product({ id: 'b', name: 'Beta', shortName: 'BETA', description: 'long term' }),
      ];
    });

    it('matches name / shortName / description (case-insensitive)', () => {
      c.applyFilter('alph'); expect(c.filtered.map(p => p.id)).toEqual(['a']);
      c.applyFilter('LONG'); expect(c.filtered.map(p => p.id)).toEqual(['b']);
    });

    it('returns all on empty term and resets page', () => {
      c.page = 2;
      c.applyFilter('  ');
      expect(c.filtered).toHaveLength(2);
      expect(c.page).toBe(0);
    });
  });

  describe('pagination', () => {
    let c: FixedDepositsListComponent;
    beforeEach(() => {
      c = make();
      c.filtered = Array.from({ length: 16 }, (_, i) => product({ id: `p${i}` }));
    });

    it('computes window and walks pages within bounds', () => {
      c.page = 0;
      expect(c.totalPages).toBe(2);
      expect(c.startRow).toBe(1);
      expect(c.endRow).toBe(15);
      expect(c.pageItems).toHaveLength(15);
      c.nextPage();
      expect(c.page).toBe(1);
      expect(c.endRow).toBe(16);
      expect(c.pageItems).toHaveLength(1);
      c.nextPage(); expect(c.page).toBe(1);
      c.prevPage(); expect(c.page).toBe(0);
    });

    it('startRow is 0 with no rows', () => {
      c.filtered = [];
      expect(c.startRow).toBe(0);
    });
  });

  it('productVariant reflects active state', () => {
    const c = make();
    expect(c.productVariant(product({ active: true }))).toBe('success');
    expect(c.productVariant(product({ active: false }))).toBe('neutral');
  });
});
