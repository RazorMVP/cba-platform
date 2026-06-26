import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { RecurringDepositDetailComponent } from './recurring-deposit-detail';
import { ProductService, RecurringDepositProduct } from '../../product.service';

type Svc = Record<
  'getRecurringDepositProduct' | 'createRecurringDepositProduct' | 'updateRecurringDepositProduct' | 'deactivateRecurringDepositProduct',
  ReturnType<typeof vi.fn>
>;

function product(over: Partial<RecurringDepositProduct> = {}): RecurringDepositProduct {
  return {
    id: 'r1', name: 'Monthly Saver', shortName: 'RD12', description: 'desc',
    currencyCode: 'USD', nominalAnnualInterestRate: 4,
    compoundingPeriod: 'MONTHLY', postingPeriod: 'MONTHLY', calculationType: 'DAILY_BALANCE',
    depositFrequency: 'MONTHLY', mandatoryRecommendedDepositAmount: 50,
    minDepositTerm: 6, minDepositTermType: 'MONTHS', maxDepositTerm: 36, maxDepositTermType: 'MONTHS',
    minDepositAmount: 10, maxDepositAmount: 5000,
    prePenaltyApplicable: true, prePenaltyInterest: 1, active: true, ...over,
  };
}

function configure(routeId: string, svc: Svc) {
  TestBed.configureTestingModule({
    imports: [RecurringDepositDetailComponent],
    providers: [
      provideRouter([]),
      { provide: ProductService, useValue: svc },
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => routeId } } } },
    ],
  });
}

describe('RecurringDepositDetailComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      getRecurringDepositProduct: vi.fn().mockReturnValue(of(product())),
      createRecurringDepositProduct: vi.fn().mockReturnValue(of(product({ id: 'new1' }))),
      updateRecurringDepositProduct: vi.fn().mockReturnValue(of(product({ name: 'Updated' }))),
      deactivateRecurringDepositProduct: vi.fn().mockReturnValue(of(void 0)),
    };
  });

  function make(routeId: string) {
    configure(routeId, svc);
    const fixture = TestBed.createComponent(RecurringDepositDetailComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads an existing product from the route', () => {
    const c = make('r1');
    expect(svc.getRecurringDepositProduct).toHaveBeenCalledWith('r1');
    expect(c.product?.id).toBe('r1');
    expect(c.isNew).toBe(false);
    expect(c.loading).toBe(false);
  });

  it('sets an error when the product is missing', () => {
    svc.getRecurringDepositProduct.mockReturnValue(throwError(() => new Error('x')));
    const c = make('r1');
    expect(c.error).toBe('Product not found.');
  });

  it('enters create mode for the "new" id without calling get', () => {
    const c = make('new');
    expect(c.isNew).toBe(true);
    expect(c.editMode).toBe(true);
    expect(c.form.name).toBe('');
    expect(c.form.depositFrequency).toBe('MONTHLY');
    expect(svc.getRecurringDepositProduct).not.toHaveBeenCalled();
  });

  it('enterEditMode copies product fields into a separate form object', () => {
    const c = make('r1');
    c.enterEditMode();
    expect(c.form.name).toBe('Monthly Saver');
    expect(c.form.depositFrequency).toBe('MONTHLY');
    expect(c.form.mandatoryRecommendedDepositAmount).toBe(50);
    c.form.name = 'Changed';
    expect(c.product!.name).toBe('Monthly Saver');
  });

  it('cancelEdit exits without navigating for an existing product', () => {
    const c = make('r1');
    const nav = vi.spyOn(TestBed.inject(Router), 'navigate');
    c.enterEditMode();
    c.cancelEdit();
    expect(c.editMode).toBe(false);
    expect(nav).not.toHaveBeenCalled();
  });

  it('save updates an existing product', () => {
    const c = make('r1');
    c.enterEditMode();
    c.save();
    expect(svc.updateRecurringDepositProduct).toHaveBeenCalledWith('r1', c.form);
    expect(c.product?.name).toBe('Updated');
    expect(c.editMode).toBe(false);
  });

  it('save creates a new product then navigates to its detail', () => {
    const c = make('new');
    const nav = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    c.form.name = 'Fresh';
    c.save();
    expect(svc.createRecurringDepositProduct).toHaveBeenCalledWith(c.form);
    expect(c.product?.id).toBe('new1');
    expect(c.isNew).toBe(false);
    expect(nav).toHaveBeenCalled();
  });

  it('save surfaces an error and keeps edit mode on', () => {
    svc.updateRecurringDepositProduct.mockReturnValue(throwError(() => new Error('x')));
    const c = make('r1');
    c.enterEditMode();
    c.save();
    expect(c.saveError).toBe('Save failed. Check all required fields.');
    expect(c.editMode).toBe(true);
  });

  it('deactivate calls the service and navigates back', () => {
    const c = make('r1');
    const nav = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    c.deactivate();
    expect(svc.deactivateRecurringDepositProduct).toHaveBeenCalledWith('r1');
    expect(nav).toHaveBeenCalled();
  });

  describe('display helpers', () => {
    it('setSection switches the active section', () => {
      const c = make('r1');
      c.setSection('frequency');
      expect(c.activeSection).toBe('frequency');
    });

    it('label maps with a fallback', () => {
      const c = make('r1');
      expect(c.label(c.frequencyLabels, 'WEEKLY')).toBe('Weekly');
      expect(c.label(c.frequencyLabels, 'NOPE')).toBe('NOPE');
    });
  });
});
