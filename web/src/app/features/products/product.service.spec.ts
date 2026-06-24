import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ApiService } from '../../core/api/api.service';
import { ProductService } from './product.service';

describe('ProductService', () => {
  let service: ProductService;
  let api: Record<'get' | 'getPage' | 'post' | 'put' | 'delete', ReturnType<typeof vi.fn>>;

  beforeEach(() => {
    api = {
      get: vi.fn().mockReturnValue(of([])),
      getPage: vi.fn().mockReturnValue(of({ content: [] })),
      post: vi.fn().mockReturnValue(of({})),
      put: vi.fn().mockReturnValue(of({})),
      delete: vi.fn().mockReturnValue(of({})),
    };
    TestBed.configureTestingModule({
      providers: [ProductService, { provide: ApiService, useValue: api }],
    });
    service = TestBed.inject(ProductService);
  });

  it('list* methods coerce the activeOnly flag to a string', () => {
    service.listLoanProducts(true).subscribe();
    expect(api.get).toHaveBeenCalledWith('/loan-products', { activeOnly: 'true' });
    service.listDepositProducts().subscribe();
    expect(api.get).toHaveBeenCalledWith('/deposit-products', { activeOnly: 'false' });
    service.listFixedDepositProducts(true).subscribe();
    expect(api.get).toHaveBeenCalledWith('/fixeddepositproducts', { activeOnly: 'true' });
    service.listRecurringDepositProducts().subscribe();
    expect(api.get).toHaveBeenCalledWith('/recurringdepositproducts', { activeOnly: 'false' });
    service.listShareProducts(true).subscribe();
    expect(api.get).toHaveBeenCalledWith('/shareproducts', { activeOnly: 'true' });
  });

  it('loan product CRUD routes correctly', () => {
    service.getLoanProduct('lp1').subscribe();
    expect(api.get).toHaveBeenCalledWith('/loan-products/lp1');
    const body = { name: 'P', shortName: 'PROD', minPrincipal: 1, maxPrincipal: 9, minInterestRate: 1, maxInterestRate: 9, defaultInterestRate: 5, minTermMonths: 1, maxTermMonths: 12 };
    service.createLoanProduct(body).subscribe();
    expect(api.post).toHaveBeenCalledWith('/loan-products', body);
    service.updateLoanProduct('lp1', body).subscribe();
    expect(api.put).toHaveBeenCalledWith('/loan-products/lp1', body);
    service.deactivateLoanProduct('lp1').subscribe();
    expect(api.delete).toHaveBeenCalledWith('/loan-products/lp1');
  });

  it('deposit / fixed / recurring / share product reads route to distinct paths', () => {
    service.getDepositProduct('d1').subscribe();
    expect(api.get).toHaveBeenCalledWith('/deposit-products/d1');
    service.getFixedDepositProduct('f1').subscribe();
    expect(api.get).toHaveBeenCalledWith('/fixeddepositproducts/f1');
    service.getRecurringDepositProduct('r1').subscribe();
    expect(api.get).toHaveBeenCalledWith('/recurringdepositproducts/r1');
    service.getShareProduct('s1').subscribe();
    expect(api.get).toHaveBeenCalledWith('/shareproducts/s1');
  });

  it('listCharges pages /charges with an optional appliesTo filter', () => {
    service.listCharges(0, 20, 'LOAN').subscribe();
    expect(api.getPage).toHaveBeenCalledWith('/charges', 0, 20, { appliesTo: 'LOAN' });
    service.listCharges().subscribe();
    expect(api.getPage).toHaveBeenCalledWith('/charges', 0, 20, {});
  });

  it('charge CRUD routes correctly', () => {
    const body = {
      name: 'Fee', currencyCode: 'USD', chargeAppliesTo: 'LOAN' as const,
      chargeTimeType: 'DISBURSEMENT' as const, chargeCalculation: 'FLAT' as const,
      amount: 10, penalty: false, active: true,
    };
    service.createCharge(body).subscribe();
    expect(api.post).toHaveBeenCalledWith('/charges', body);
    service.updateCharge('ch1', body).subscribe();
    expect(api.put).toHaveBeenCalledWith('/charges/ch1', body);
    service.deleteCharge('ch1').subscribe();
    expect(api.delete).toHaveBeenCalledWith('/charges/ch1');
  });
});
