import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { DepositProductDetailComponent } from './deposit-product-detail';
import { ProductService, DepositProduct } from '../../product.service';

type Svc = Record<
  'getDepositProduct' | 'createDepositProduct' | 'updateDepositProduct' | 'deactivateDepositProduct',
  ReturnType<typeof vi.fn>
>;

function product(over: Partial<DepositProduct> = {}): DepositProduct {
  return {
    id: 'd1', name: 'Standard Savings', shortName: 'SAV', description: 'desc',
    accountType: 'SAVINGS', currencyCode: 'USD', minimumBalance: 0,
    interestRate: 2, interestCompounding: 'MONTHLY', interestPostingPeriodType: 'MONTHLY',
    daysInYearType: 'ACTUAL', daysInMonthType: 'ACTUAL',
    withdrawalFeeForTransfers: false, allowOverdraft: false, accountingType: 'NONE',
    savingsReferenceAccount: { id: 'gl1', glCode: '2000', name: 'Savings Ref' },
    charges: [{ id: 'c1', name: 'Fee', chargeTimeType: 'WITHDRAWAL_FEE' }],
    active: true, ...over,
  };
}

function configure(routeId: string, svc: Svc) {
  TestBed.configureTestingModule({
    imports: [DepositProductDetailComponent],
    providers: [
      provideRouter([]),
      { provide: ProductService, useValue: svc },
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => routeId } } } },
    ],
  });
}

describe('DepositProductDetailComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      getDepositProduct: vi.fn().mockReturnValue(of(product())),
      createDepositProduct: vi.fn().mockReturnValue(of(product({ id: 'new1' }))),
      updateDepositProduct: vi.fn().mockReturnValue(of(product({ name: 'Updated' }))),
      deactivateDepositProduct: vi.fn().mockReturnValue(of(void 0)),
    };
  });

  function make(routeId: string) {
    configure(routeId, svc);
    const fixture = TestBed.createComponent(DepositProductDetailComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads an existing product from the route', () => {
    const c = make('d1');
    expect(svc.getDepositProduct).toHaveBeenCalledWith('d1');
    expect(c.product?.id).toBe('d1');
    expect(c.isNew).toBe(false);
    expect(c.loading).toBe(false);
  });

  it('sets an error when the product is missing', () => {
    svc.getDepositProduct.mockReturnValue(throwError(() => new Error('x')));
    const c = make('d1');
    expect(c.error).toBe('Product not found.');
  });

  it('enters create mode for the "new" id without calling get', () => {
    const c = make('new');
    expect(c.isNew).toBe(true);
    expect(c.editMode).toBe(true);
    expect(c.form.name).toBe('');
    expect(c.form.accountType).toBe('SAVINGS');
    expect(svc.getDepositProduct).not.toHaveBeenCalled();
  });

  it('enterEditMode deep-copies the product into the form', () => {
    const c = make('d1');
    c.enterEditMode();
    expect(c.form.name).toBe('Standard Savings');
    expect(c.form.savingsReferenceAccountId).toBe('gl1');
    expect(c.form.chargeIds).toEqual(['c1']);
    c.form.chargeIds!.push('c2');
    expect(c.product!.charges).toHaveLength(1);
  });

  it('cancelEdit exits without navigating for an existing product', () => {
    const c = make('d1');
    const nav = vi.spyOn(TestBed.inject(Router), 'navigate');
    c.enterEditMode();
    c.cancelEdit();
    expect(c.editMode).toBe(false);
    expect(nav).not.toHaveBeenCalled();
  });

  it('save updates an existing product and writes it back', () => {
    const c = make('d1');
    c.enterEditMode();
    c.save();
    expect(svc.updateDepositProduct).toHaveBeenCalledWith('d1', c.form);
    expect(c.product?.name).toBe('Updated');
    expect(c.editMode).toBe(false);
  });

  it('save creates a new product then navigates to its detail', () => {
    const c = make('new');
    const nav = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    c.form.name = 'Fresh';
    c.save();
    expect(svc.createDepositProduct).toHaveBeenCalledWith(c.form);
    expect(c.product?.id).toBe('new1');
    expect(c.isNew).toBe(false);
    expect(nav).toHaveBeenCalled();
  });

  it('save surfaces an error and keeps edit mode on', () => {
    svc.updateDepositProduct.mockReturnValue(throwError(() => new Error('x')));
    const c = make('d1');
    c.enterEditMode();
    c.save();
    expect(c.saveError).toBe('Save failed. Check all required fields.');
    expect(c.editMode).toBe(true);
  });

  it('deactivate calls the service and navigates back', () => {
    const c = make('d1');
    const nav = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    c.confirmDeactivate();
    expect(c.showDeactivateConfirm).toBe(true);
    c.deactivate();
    expect(svc.deactivateDepositProduct).toHaveBeenCalledWith('d1');
    expect(nav).toHaveBeenCalled();
  });

  describe('display helpers', () => {
    it('setSection switches the active section', () => {
      const c = make('d1');
      c.setSection('overdraft');
      expect(c.activeSection).toBe('overdraft');
    });

    it('label maps with a fallback', () => {
      const c = make('d1');
      expect(c.label(c.accountTypeLabels, 'CHECKING')).toBe('Checking');
      expect(c.label(c.compoundingLabels, 'NOPE')).toBe('NOPE');
    });

    it('exposes the GL account field descriptors', () => {
      const c = make('d1');
      expect(c.glAccountFields.length).toBe(8);
      expect(c.glAccountFields[0]).toEqual({ key: 'savingsReferenceAccount', label: 'Savings Reference' });
    });
  });
});
