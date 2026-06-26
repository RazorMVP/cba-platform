import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { SharesListComponent } from './shares-list';
import { ProductService, ShareProduct } from '../product.service';

type Svc = Record<'listShareProducts', ReturnType<typeof vi.fn>>;

function product(over: Partial<ShareProduct> = {}): ShareProduct {
  return {
    id: 's1', name: 'Ordinary Shares', shortName: 'ORD', description: 'desc',
    currencyCode: 'USD', sharesIssued: 1000, unitPrice: 10,
    allowDividendsForInactive: false, active: true, ...over,
  };
}

describe('SharesListComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = { listShareProducts: vi.fn().mockReturnValue(of([product()])) };
    TestBed.configureTestingModule({
      imports: [SharesListComponent],
      providers: [provideRouter([]), { provide: ProductService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(SharesListComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads products on init', () => {
    const c = make();
    expect(svc.listShareProducts).toHaveBeenCalledWith(false);
    expect(c.products).toHaveLength(1);
    expect(c.filtered).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('clears loading on error', () => {
    svc.listShareProducts.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.loading).toBe(false);
  });

  it('onActiveFilterChange reloads with the activeOnly flag', () => {
    const c = make();
    c.activeOnly = true;
    c.onActiveFilterChange();
    expect(svc.listShareProducts).toHaveBeenLastCalledWith(true);
  });

  describe('applyFilter', () => {
    let c: SharesListComponent;
    beforeEach(() => {
      c = make();
      c.products = [
        product({ id: 'a', name: 'Alpha Shares', shortName: 'ALPH' }),
        product({ id: 'b', name: 'Beta Equity', shortName: 'BETA', description: 'preference' }),
      ];
    });

    it('matches name / shortName / description (case-insensitive)', () => {
      c.applyFilter('equity'); expect(c.filtered.map(p => p.id)).toEqual(['b']);
      c.applyFilter('PREFERENCE'); expect(c.filtered.map(p => p.id)).toEqual(['b']);
      c.applyFilter('alph'); expect(c.filtered.map(p => p.id)).toEqual(['a']);
    });

    it('returns all on empty term and resets page', () => {
      c.page = 2;
      c.applyFilter('');
      expect(c.filtered).toHaveLength(2);
      expect(c.page).toBe(0);
    });
  });

  describe('pagination', () => {
    let c: SharesListComponent;
    beforeEach(() => {
      c = make();
      c.filtered = Array.from({ length: 30 }, (_, i) => product({ id: `p${i}` }));
    });

    it('computes window and walks pages within bounds', () => {
      c.page = 0;
      expect(c.totalPages).toBe(2);
      expect(c.endRow).toBe(15);
      expect(c.pageItems).toHaveLength(15);
      c.nextPage();
      expect(c.page).toBe(1);
      expect(c.startRow).toBe(16);
      expect(c.endRow).toBe(30);
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
