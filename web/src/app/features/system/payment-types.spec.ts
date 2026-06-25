import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { PaymentTypesComponent } from './payment-types';
import { SystemService, SystemPaymentType } from './system.service';

type Svc = Record<
  'listPaymentTypes' | 'createPaymentType' | 'updatePaymentType' | 'deletePaymentType',
  ReturnType<typeof vi.fn>
>;

function pt(over: Partial<SystemPaymentType> = {}): SystemPaymentType {
  return { id: 'pt1', name: 'Cash', description: 'desc', cashPayment: true, position: 1, systemDefined: false, ...over };
}
function page(content: SystemPaymentType[], totalElements: number) {
  return of({ content, totalElements, totalPages: Math.max(1, Math.ceil(totalElements / 20)), size: 20, number: 0 });
}

describe('PaymentTypesComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listPaymentTypes: vi.fn().mockReturnValue(page([pt()], 1)),
      createPaymentType: vi.fn().mockReturnValue(of(pt({ id: 'pt2' }))),
      updatePaymentType: vi.fn().mockReturnValue(of(pt({ id: 'pt1' }))),
      deletePaymentType: vi.fn().mockReturnValue(of(void 0)),
    };
    TestBed.configureTestingModule({
      imports: [PaymentTypesComponent],
      providers: [provideRouter([]), { provide: SystemService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(PaymentTypesComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads the first page on init', () => {
    const c = make();
    expect(svc.listPaymentTypes).toHaveBeenCalledWith(0);
    expect(c.types).toHaveLength(1);
    expect(c.total).toBe(1);
    expect(c.loading).toBe(false);
  });

  it('sets an error when loading fails', () => {
    svc.listPaymentTypes.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.error).toBe('Failed to load payment types.');
  });

  describe('save', () => {
    it('creates when no edit target then reloads', () => {
      const c = make();
      c.openCreate();
      c.form = { name: 'Cheque', description: '', cashPayment: false, position: 2 };
      c.save();
      expect(svc.createPaymentType).toHaveBeenCalledWith(c.form);
      expect(c.activeModal).toBeNull();
      expect(svc.listPaymentTypes).toHaveBeenCalledTimes(2);
    });

    it('updates when an edit target is set', () => {
      const c = make();
      c.openEdit(pt({ id: 'pt1' }));
      c.save();
      expect(svc.updatePaymentType).toHaveBeenCalledWith('pt1', c.form);
    });

    it('surfaces an error on failure', () => {
      svc.createPaymentType.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.openCreate();
      c.save();
      expect(c.modalError).toBe('Save failed. Please try again.');
      expect(c.working).toBe(false);
    });
  });

  describe('confirmDelete', () => {
    it('deletes the target then reloads', () => {
      const c = make();
      c.openDelete(pt({ id: 'pt1' }));
      c.confirmDelete();
      expect(svc.deletePaymentType).toHaveBeenCalledWith('pt1');
      expect(svc.listPaymentTypes).toHaveBeenCalledTimes(2);
    });

    it('does nothing without a target', () => {
      const c = make();
      c.deleteTarget = null;
      c.confirmDelete();
      expect(svc.deletePaymentType).not.toHaveBeenCalled();
    });
  });

  describe('pagination', () => {
    let c: PaymentTypesComponent;
    beforeEach(() => {
      svc.listPaymentTypes.mockReturnValue(page([], 50));
      c = TestBed.createComponent(PaymentTypesComponent).componentInstance;
      c.total = 50; // totalPages = 3
    });

    it('next advances but never past the last page', () => {
      c.page = 0; c.next(); expect(c.page).toBe(1);
      c.next(); expect(c.page).toBe(2);
      c.next(); expect(c.page).toBe(2);
    });

    it('prev decrements but never below 0', () => {
      c.page = 1; c.prev(); expect(c.page).toBe(0);
      c.prev(); expect(c.page).toBe(0);
    });

    it('totalPages divides total by page size', () => {
      expect(c.totalPages).toBe(3);
    });
  });
});
