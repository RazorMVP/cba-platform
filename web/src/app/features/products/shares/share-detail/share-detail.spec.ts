import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ShareDetailComponent } from './share-detail';
import { ProductService, ShareProduct } from '../../product.service';

type Svc = Record<
  'getShareProduct' | 'createShareProduct' | 'updateShareProduct' | 'deactivateShareProduct',
  ReturnType<typeof vi.fn>
>;

function product(over: Partial<ShareProduct> = {}): ShareProduct {
  return {
    id: 's1', name: 'Ordinary Shares', shortName: 'ORD', description: 'desc',
    currencyCode: 'USD', totalShares: 10000, sharesIssued: 1000, unitPrice: 10,
    nominalShares: 100, minimumShares: 1, maximumShares: 500,
    lockInPeriodFrequency: 6, lockInPeriodFrequencyType: 'MONTHS',
    allowDividendsForInactive: true, active: true, ...over,
  };
}

function configure(routeId: string, svc: Svc) {
  TestBed.configureTestingModule({
    imports: [ShareDetailComponent],
    providers: [
      provideRouter([]),
      { provide: ProductService, useValue: svc },
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => routeId } } } },
    ],
  });
}

describe('ShareDetailComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      getShareProduct: vi.fn().mockReturnValue(of(product())),
      createShareProduct: vi.fn().mockReturnValue(of(product({ id: 'new1' }))),
      updateShareProduct: vi.fn().mockReturnValue(of(product({ name: 'Updated' }))),
      deactivateShareProduct: vi.fn().mockReturnValue(of(void 0)),
    };
  });

  function make(routeId: string) {
    configure(routeId, svc);
    const fixture = TestBed.createComponent(ShareDetailComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads an existing product from the route', () => {
    const c = make('s1');
    expect(svc.getShareProduct).toHaveBeenCalledWith('s1');
    expect(c.product?.id).toBe('s1');
    expect(c.isNew).toBe(false);
    expect(c.loading).toBe(false);
  });

  it('sets an error when the product is missing', () => {
    svc.getShareProduct.mockReturnValue(throwError(() => new Error('x')));
    const c = make('s1');
    expect(c.error).toBe('Product not found.');
  });

  it('enters create mode for the "new" id without calling get', () => {
    const c = make('new');
    expect(c.isNew).toBe(true);
    expect(c.editMode).toBe(true);
    expect(c.form.name).toBe('');
    expect(c.form.allowDividendsForInactive).toBe(false);
    expect(svc.getShareProduct).not.toHaveBeenCalled();
  });

  it('enterEditMode copies product fields (incl. dividend toggle) into a separate form', () => {
    const c = make('s1');
    c.enterEditMode();
    expect(c.form.name).toBe('Ordinary Shares');
    expect(c.form.unitPrice).toBe(10);
    expect(c.form.allowDividendsForInactive).toBe(true);
    c.form.name = 'Changed';
    expect(c.product!.name).toBe('Ordinary Shares');
  });

  it('cancelEdit exits without navigating for an existing product', () => {
    const c = make('s1');
    const nav = vi.spyOn(TestBed.inject(Router), 'navigate');
    c.enterEditMode();
    c.cancelEdit();
    expect(c.editMode).toBe(false);
    expect(nav).not.toHaveBeenCalled();
  });

  it('save updates an existing product', () => {
    const c = make('s1');
    c.enterEditMode();
    c.save();
    expect(svc.updateShareProduct).toHaveBeenCalledWith('s1', c.form);
    expect(c.product?.name).toBe('Updated');
    expect(c.editMode).toBe(false);
  });

  it('save creates a new product then navigates to its detail', () => {
    const c = make('new');
    const nav = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    c.form.name = 'Fresh';
    c.form.shortName = 'FRSH';
    c.save();
    expect(svc.createShareProduct).toHaveBeenCalledWith(c.form);
    expect(c.product?.id).toBe('new1');
    expect(c.isNew).toBe(false);
    expect(nav).toHaveBeenCalled();
  });

  it('save surfaces an error and keeps edit mode on', () => {
    svc.updateShareProduct.mockReturnValue(throwError(() => new Error('x')));
    const c = make('s1');
    c.enterEditMode();
    c.save();
    expect(c.saveError).toBe('Save failed. Check all required fields.');
    expect(c.editMode).toBe(true);
  });

  it('deactivate calls the service and navigates back', () => {
    const c = make('s1');
    const nav = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    c.deactivate();
    expect(svc.deactivateShareProduct).toHaveBeenCalledWith('s1');
    expect(nav).toHaveBeenCalled();
  });

  describe('display helpers', () => {
    it('setSection switches the active section', () => {
      const c = make('s1');
      c.setSection('shares');
      expect(c.activeSection).toBe('shares');
    });

    it('label maps with a fallback', () => {
      const c = make('s1');
      expect(c.label(c.periodTypeLabels, 'MONTHS')).toBe('Months');
      expect(c.label(c.periodTypeLabels, 'NOPE')).toBe('NOPE');
    });
  });
});
