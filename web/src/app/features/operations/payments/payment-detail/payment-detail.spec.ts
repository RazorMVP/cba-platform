import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { PaymentDetailComponent } from './payment-detail';
import { PaymentService, Payment } from '../payment.service';

type Svc = Record<'get' | 'reverse', ReturnType<typeof vi.fn>>;

function payment(over: Partial<Payment> = {}): Payment {
  return {
    id: 'pay-1', referenceNumber: 'REF001', paymentType: 'INTERNAL_TRANSFER',
    sourceAccountId: 'acc-1', sourceAccountNumber: '001-SAV-0001',
    destinationAccountId: 'acc-2', destinationAccountNumber: '001-SAV-0002',
    amount: 250, currencyCode: 'USD', status: 'COMPLETED',
    createdAt: '2026-06-01', crossCurrency: false, ...over,
  };
}

describe('PaymentDetailComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      get: vi.fn().mockReturnValue(of(payment())),
      reverse: vi.fn().mockReturnValue(of(payment({ status: 'REVERSED' }))),
    };
    TestBed.configureTestingModule({
      imports: [PaymentDetailComponent],
      providers: [
        provideRouter([]),
        { provide: PaymentService, useValue: svc },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'pay-1' } } } },
      ],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(PaymentDetailComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads the payment from the route id on init and renders', () => {
    const c = make();
    expect(svc.get).toHaveBeenCalledWith('pay-1');
    expect(c.payment?.id).toBe('pay-1');
    expect(c.loading).toBe(false);
    expect(c.error).toBe('');
  });

  it('sets an error message when the payment is not found', () => {
    svc.get.mockReturnValue(throwError(() => new Error('404')));
    const c = make();
    expect(c.payment).toBeNull();
    expect(c.error).toBe('Payment not found.');
    expect(c.loading).toBe(false);
  });

  describe('reverse modal', () => {
    it('openReverseModal resets state and opens', () => {
      const c = make();
      c.reverseReason = 'stale';
      c.reverseError = 'old';
      c.openReverseModal();
      expect(c.showReverseModal).toBe(true);
      expect(c.reverseReason).toBe('');
      expect(c.reverseError).toBe('');
      expect(c.reverseWorking).toBe(false);
    });

    it('submitReverse is a no-op without a reason', () => {
      const c = make();
      c.reverseReason = '   ';
      c.submitReverse();
      expect(svc.reverse).not.toHaveBeenCalled();
    });

    it('submitReverse calls the service with the trimmed reason and closes on success', () => {
      const c = make();
      c.openReverseModal();
      c.reverseReason = '  duplicate charge  ';
      c.submitReverse();
      expect(svc.reverse).toHaveBeenCalledWith('pay-1', 'duplicate charge');
      expect(c.payment?.status).toBe('REVERSED');
      expect(c.showReverseModal).toBe(false);
      expect(c.reverseWorking).toBe(false);
    });

    it('submitReverse surfaces an error and keeps the modal open on failure', () => {
      svc.reverse.mockReturnValue(throwError(() => new Error('boom')));
      const c = make();
      c.openReverseModal();
      c.reverseReason = 'oops';
      c.submitReverse();
      expect(c.reverseError).toContain('Reversal failed');
      expect(c.reverseWorking).toBe(false);
    });

    it('closeReverseModal does nothing while a reversal is in flight', () => {
      const c = make();
      c.showReverseModal = true;
      c.reverseWorking = true;
      c.closeReverseModal();
      expect(c.showReverseModal).toBe(true);
      c.reverseWorking = false;
      c.closeReverseModal();
      expect(c.showReverseModal).toBe(false);
    });
  });

  describe('display helpers', () => {
    let c: PaymentDetailComponent;
    beforeEach(() => { c = make(); });

    it('statusVariant maps statuses to badge variants', () => {
      expect(c.statusVariant('COMPLETED')).toBe('success');
      expect(c.statusVariant('PENDING')).toBe('warning');
      expect(c.statusVariant('PROCESSING')).toBe('warning');
      expect(c.statusVariant('FAILED')).toBe('error');
      expect(c.statusVariant('REVERSED')).toBe('neutral');
    });

    it('canReverse is true only for COMPLETED payments', () => {
      c.payment = payment({ status: 'COMPLETED' });
      expect(c.canReverse).toBe(true);
      c.payment = payment({ status: 'REVERSED' });
      expect(c.canReverse).toBe(false);
      c.payment = null;
      expect(c.canReverse).toBe(false);
    });
  });
});
