import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { FixedDepositDetailComponent } from './fixed-deposit-detail';
import { ProductService, FixedDepositProduct } from '../../product.service';

type Svc = Record<
  'getFixedDepositProduct' | 'createFixedDepositProduct' | 'updateFixedDepositProduct' | 'deactivateFixedDepositProduct',
  ReturnType<typeof vi.fn>
>;

function product(over: Partial<FixedDepositProduct> = {}): FixedDepositProduct {
  return {
    id: 'f1', name: 'Term Deposit', shortName: 'TD12', description: 'desc',
    currencyCode: 'USD', nominalAnnualInterestRate: 5,
    compoundingPeriod: 'MONTHLY', postingPeriod: 'MONTHLY', calculationType: 'DAILY_BALANCE',
    minDepositTerm: 1, minDepositTermType: 'MONTHS', maxDepositTerm: 24, maxDepositTermType: 'MONTHS',
    minDepositAmount: 100, maxDepositAmount: 100000,
    prePenaltyApplicable: true, prePenaltyInterest: 1.5, active: true, ...over,
  };
}

function configure(routeId: string, svc: Svc) {
  TestBed.configureTestingModule({
    imports: [FixedDepositDetailComponent],
    providers: [
      provideRouter([]),
      { provide: ProductService, useValue: svc },
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => routeId } } } },
    ],
  });
}

describe('FixedDepositDetailComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      getFixedDepositProduct: vi.fn().mockReturnValue(of(product())),
      createFixedDepositProduct: vi.fn().mockReturnValue(of(product({ id: 'new1' }))),
      updateFixedDepositProduct: vi.fn().mockReturnValue(of(product({ name: 'Updated' }))),
      deactivateFixedDepositProduct: vi.fn().mockReturnValue(of(void 0)),
    };
  });

  function make(routeId: string) {
    configure(routeId, svc);
    const fixture = TestBed.createComponent(FixedDepositDetailComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads an existing product from the route', () => {
    const c = make('f1');
    expect(svc.getFixedDepositProduct).toHaveBeenCalledWith('f1');
    expect(c.product?.id).toBe('f1');
    expect(c.isNew).toBe(false);
    expect(c.loading).toBe(false);
  });

  it('sets an error when the product is missing', () => {
    svc.getFixedDepositProduct.mockReturnValue(throwError(() => new Error('x')));
    const c = make('f1');
    expect(c.error).toBe('Product not found.');
  });

  it('enters create mode for the "new" id without calling get', () => {
    const c = make('new');
    expect(c.isNew).toBe(true);
    expect(c.editMode).toBe(true);
    expect(c.form.name).toBe('');
    expect(c.form.minDepositTermType).toBe('MONTHS');
    expect(svc.getFixedDepositProduct).not.toHaveBeenCalled();
  });

  it('enterEditMode copies product fields into a separate form object', () => {
    const c = make('f1');
    c.enterEditMode();
    expect(c.form.name).toBe('Term Deposit');
    expect(c.form.nominalAnnualInterestRate).toBe(5);
    expect(c.form.prePenaltyInterest).toBe(1.5);
    c.form.name = 'Changed';
    expect(c.product!.name).toBe('Term Deposit');
  });

  it('cancelEdit exits without navigating for an existing product', () => {
    const c = make('f1');
    const nav = vi.spyOn(TestBed.inject(Router), 'navigate');
    c.enterEditMode();
    c.cancelEdit();
    expect(c.editMode).toBe(false);
    expect(nav).not.toHaveBeenCalled();
  });

  it('save updates an existing product', () => {
    const c = make('f1');
    c.enterEditMode();
    c.save();
    expect(svc.updateFixedDepositProduct).toHaveBeenCalledWith('f1', c.form);
    expect(c.product?.name).toBe('Updated');
    expect(c.editMode).toBe(false);
  });

  it('save creates a new product then navigates to its detail', () => {
    const c = make('new');
    const nav = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    c.form.name = 'Fresh';
    c.save();
    expect(svc.createFixedDepositProduct).toHaveBeenCalledWith(c.form);
    expect(c.product?.id).toBe('new1');
    expect(c.isNew).toBe(false);
    expect(nav).toHaveBeenCalled();
  });

  it('save surfaces an error and keeps edit mode on', () => {
    svc.updateFixedDepositProduct.mockReturnValue(throwError(() => new Error('x')));
    const c = make('f1');
    c.enterEditMode();
    c.save();
    expect(c.saveError).toBe('Save failed. Check all required fields.');
    expect(c.editMode).toBe(true);
  });

  it('deactivate calls the service and navigates back', () => {
    const c = make('f1');
    const nav = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    c.deactivate();
    expect(svc.deactivateFixedDepositProduct).toHaveBeenCalledWith('f1');
    expect(nav).toHaveBeenCalled();
  });

  describe('display helpers', () => {
    it('setSection switches the active section', () => {
      const c = make('f1');
      c.setSection('penalty');
      expect(c.activeSection).toBe('penalty');
    });

    it('label maps with a fallback', () => {
      const c = make('f1');
      expect(c.label(c.calcTypeLabels, 'DAILY_BALANCE')).toBe('Daily Balance');
      expect(c.label(c.termTypeLabels, 'NOPE')).toBe('NOPE');
    });
  });
});
