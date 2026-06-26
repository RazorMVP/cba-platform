import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { LoanProductDetailComponent } from './loan-product-detail';
import { ProductService, LoanProduct } from '../../product.service';

type Svc = Record<
  'getLoanProduct' | 'createLoanProduct' | 'updateLoanProduct' | 'deactivateLoanProduct',
  ReturnType<typeof vi.fn>
>;

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
    fundSourceAccount: { id: 'gl1', glCode: '1000', name: 'Fund Source' },
    charges: [{ id: 'c1', name: 'Fee', chargeTimeType: 'DISBURSEMENT' }],
    active: true, ...over,
  };
}

function configure(routeId: string, svc: Svc) {
  TestBed.configureTestingModule({
    imports: [LoanProductDetailComponent],
    providers: [
      provideRouter([]),
      { provide: ProductService, useValue: svc },
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => routeId } } } },
    ],
  });
}

describe('LoanProductDetailComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      getLoanProduct: vi.fn().mockReturnValue(of(product())),
      createLoanProduct: vi.fn().mockReturnValue(of(product({ id: 'new1' }))),
      updateLoanProduct: vi.fn().mockReturnValue(of(product({ name: 'Updated' }))),
      deactivateLoanProduct: vi.fn().mockReturnValue(of(void 0)),
    };
  });

  function make(routeId: string) {
    configure(routeId, svc);
    const fixture = TestBed.createComponent(LoanProductDetailComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  describe('existing id', () => {
    it('loads the product from the route', () => {
      const c = make('lp1');
      expect(svc.getLoanProduct).toHaveBeenCalledWith('lp1');
      expect(c.product?.id).toBe('lp1');
      expect(c.isNew).toBe(false);
      expect(c.editMode).toBe(false);
      expect(c.loading).toBe(false);
    });

    it('sets an error when the product is missing', () => {
      svc.getLoanProduct.mockReturnValue(throwError(() => new Error('x')));
      const c = make('lp1');
      expect(c.error).toBe('Product not found.');
      expect(c.loading).toBe(false);
    });
  });

  describe('new id', () => {
    it('enters create mode with a blank form (does not call get)', () => {
      const c = make('new');
      expect(c.isNew).toBe(true);
      expect(c.editMode).toBe(true);
      expect(c.product).toBeNull();
      expect(c.form.name).toBe('');
      expect(c.form.allowAttributeOverrides).toBeDefined();
      expect(svc.getLoanProduct).not.toHaveBeenCalled();
    });
  });

  describe('enterEditMode', () => {
    it('deep-copies the product into the form (mutating form does not touch product)', () => {
      const c = make('lp1');
      c.enterEditMode();
      expect(c.editMode).toBe(true);
      expect(c.form.name).toBe('Standard Loan');
      expect(c.form.fundSourceAccountId).toBe('gl1');
      expect(c.form.chargeIds).toEqual(['c1']);
      // overrides object is cloned, not shared
      c.form.allowAttributeOverrides!.interestType = false;
      expect(c.product!.allowAttributeOverrides.interestType).toBe(true);
      c.form.chargeIds!.push('c2');
      expect(c.product!.charges).toHaveLength(1);
    });
  });

  describe('cancelEdit', () => {
    it('exits edit mode without navigating for an existing product', () => {
      const c = make('lp1');
      const nav = vi.spyOn(TestBed.inject(Router), 'navigate');
      c.enterEditMode();
      c.cancelEdit();
      expect(c.editMode).toBe(false);
      expect(nav).not.toHaveBeenCalled();
    });

    it('navigates away when cancelling a new product', () => {
      const c = make('new');
      const nav = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
      c.cancelEdit();
      expect(nav).toHaveBeenCalled();
    });
  });

  describe('save', () => {
    it('updates an existing product and writes the result back', () => {
      const c = make('lp1');
      c.enterEditMode();
      c.save();
      expect(svc.updateLoanProduct).toHaveBeenCalledWith('lp1', c.form);
      expect(c.product?.name).toBe('Updated');
      expect(c.editMode).toBe(false);
      expect(c.saving).toBe(false);
    });

    it('creates a new product then navigates to its detail', () => {
      const c = make('new');
      const nav = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
      c.form.name = 'Fresh';
      c.save();
      expect(svc.createLoanProduct).toHaveBeenCalledWith(c.form);
      expect(c.product?.id).toBe('new1');
      expect(c.isNew).toBe(false);
      expect(nav).toHaveBeenCalled();
    });

    it('surfaces an error and leaves edit mode on', () => {
      svc.updateLoanProduct.mockReturnValue(throwError(() => new Error('x')));
      const c = make('lp1');
      c.enterEditMode();
      c.save();
      expect(c.saveError).toBe('Save failed. Check all required fields.');
      expect(c.editMode).toBe(true);
      expect(c.saving).toBe(false);
    });
  });

  describe('deactivate', () => {
    it('confirmDeactivate opens the dialog', () => {
      const c = make('lp1');
      c.confirmDeactivate();
      expect(c.showDeactivateConfirm).toBe(true);
    });

    it('deactivate calls the service and navigates back', () => {
      const c = make('lp1');
      const nav = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
      c.deactivate();
      expect(svc.deactivateLoanProduct).toHaveBeenCalledWith('lp1');
      expect(nav).toHaveBeenCalled();
    });

    it('deactivate is a no-op without a product', () => {
      const c = make('lp1');
      c.product = null;
      c.deactivate();
      expect(svc.deactivateLoanProduct).not.toHaveBeenCalled();
    });
  });

  describe('display helpers', () => {
    it('setSection switches the active accordion section', () => {
      const c = make('lp1');
      c.setSection('interest');
      expect(c.activeSection).toBe('interest');
    });

    it('label resolves from a map with a fallback', () => {
      const c = make('lp1');
      expect(c.label(c.repaymentTypeLabels, 'ANNUITY')).toBe('Annuity (EMI)');
      expect(c.label(c.repaymentTypeLabels, 'NOPE')).toBe('NOPE');
    });
  });
});
