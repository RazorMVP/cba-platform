import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { PaymentsListComponent } from './payments-list';
import { PaymentService } from './payment.service';
import { AccountService } from '../accounts/account.service';

describe('PaymentsListComponent', () => {
  let paymentSvc: Record<'getAccountPayments' | 'transfer' | 'createStandingOrder' | 'initiateExternalPayment', ReturnType<typeof vi.fn>>;
  let accountSvc: { list: ReturnType<typeof vi.fn> };

  const acct = (id: string) => ({ id, accountNumber: id, customerName: 'X', accountType: 'SAVINGS', status: 'ACTIVE', balance: 0, currencyCode: 'USD', productId: 'p', openedDate: 'x' });

  beforeEach(() => {
    paymentSvc = {
      getAccountPayments: vi.fn().mockReturnValue(of({ content: [], totalElements: 0, totalPages: 0, size: 20, number: 0 })),
      transfer: vi.fn().mockReturnValue(of({ id: 'p1', sourceAccountId: 's', destinationAccountId: 'd' })),
      createStandingOrder: vi.fn().mockReturnValue(of({ id: 'so1' })),
      initiateExternalPayment: vi.fn().mockReturnValue(of({ id: 'e1', sourceAccountId: 's' })),
    };
    accountSvc = { list: vi.fn().mockReturnValue(of({ content: [], totalElements: 0, totalPages: 0, size: 20, number: 0 })) };

    TestBed.configureTestingModule({
      imports: [PaymentsListComponent],
      providers: [
        provideRouter([]),
        { provide: PaymentService, useValue: paymentSvc },
        { provide: AccountService, useValue: accountSvc },
      ],
    });
  });

  it('renders without a selected account (no payments loaded)', () => {
    const fixture = TestBed.createComponent(PaymentsListComponent);
    fixture.detectChanges();
    expect(paymentSvc.getAccountPayments).not.toHaveBeenCalled();
    expect(fixture.componentInstance.payments).toEqual([]);
  });

  it('selectContextAccount loads that account’s payments', () => {
    paymentSvc.getAccountPayments.mockReturnValue(
      of({ content: [{ id: 'p1', status: 'COMPLETED' }], totalElements: 1, totalPages: 1, size: 20, number: 0 }),
    );
    const c = TestBed.createComponent(PaymentsListComponent).componentInstance;
    c.selectContextAccount(acct('a1') as never);
    expect(c.selectedAccount?.id).toBe('a1');
    expect(paymentSvc.getAccountPayments).toHaveBeenCalledWith('a1', 0, 20);
    expect(c.payments).toHaveLength(1);
  });

  it('loadPayments is a no-op without a selected account', () => {
    const c = TestBed.createComponent(PaymentsListComponent).componentInstance;
    c.loadPayments();
    expect(paymentSvc.getAccountPayments).not.toHaveBeenCalled();
  });

  it('filterPayments respects the status filter', () => {
    const c = TestBed.createComponent(PaymentsListComponent).componentInstance;
    const rows = [{ status: 'COMPLETED' }, { status: 'FAILED' }] as never[];
    expect(c.filterPayments(rows)).toHaveLength(2);
    c.statusFilter = 'FAILED';
    expect(c.filterPayments(rows)).toEqual([{ status: 'FAILED' }]);
  });

  it('pagination getters (totalPages floors at 1)', () => {
    const c = TestBed.createComponent(PaymentsListComponent).componentInstance;
    c.total = 0;
    expect(c.totalPages).toBe(1);
    expect(c.startRow).toBe(0);
    c.total = 50; c.page = 2;
    expect(c.totalPages).toBe(3);
    expect(c.startRow).toBe(41);
    expect(c.endRow).toBe(50);
  });

  describe('transfer wizard', () => {
    it('step validity gates on accounts being distinct and amount > 0', () => {
      const c = TestBed.createComponent(PaymentsListComponent).componentInstance;
      c.srcAccount = acct('a1') as never;
      c.dstAccount = acct('a1') as never; // same → invalid
      expect(c.transferStep1Valid).toBe(false);
      c.dstAccount = acct('a2') as never;
      expect(c.transferStep1Valid).toBe(true);
      c.transferAmount = 0;
      expect(c.transferStep2Valid).toBe(false);
      c.transferAmount = 10;
      expect(c.transferStep2Valid).toBe(true);
    });

    it('nextStep/prevStep stay within 1..3', () => {
      const c = TestBed.createComponent(PaymentsListComponent).componentInstance;
      c.transferStep = 1; c.prevStep(); expect(c.transferStep).toBe(1);
      c.nextStep(); c.nextStep(); c.nextStep(); expect(c.transferStep).toBe(3);
    });

    it('submitTransfer builds the request and closes on success', () => {
      const c = TestBed.createComponent(PaymentsListComponent).componentInstance;
      c.srcAccount = acct('s') as never;
      c.dstAccount = acct('d') as never;
      c.transferAmount = 100;
      c.transferDescription = 'rent';
      c.submitTransfer();
      expect(paymentSvc.transfer).toHaveBeenCalledWith({
        sourceAccountId: 's', destinationAccountId: 'd', amount: 100, description: 'rent',
      });
      expect(c.activeModal).toBeNull();
      expect(c.modalWorking).toBe(false);
    });

    it('submitTransfer surfaces an error message on failure', () => {
      paymentSvc.transfer.mockReturnValue(throwError(() => new Error('insufficient')));
      const c = TestBed.createComponent(PaymentsListComponent).componentInstance;
      c.srcAccount = acct('s') as never;
      c.dstAccount = acct('d') as never;
      c.transferAmount = 100;
      c.submitTransfer();
      expect(c.modalError).toContain('Transfer failed');
      expect(c.modalWorking).toBe(false);
    });
  });

  it('standing-order + external form validity gate on required fields', () => {
    const c = TestBed.createComponent(PaymentsListComponent).componentInstance;
    c.soSrcAccount = acct('s') as never;
    c.soDstAccount = acct('d') as never;
    c.soAmount = 50;
    c.soStartDate = '2026-01-01';
    expect(c.soFormValid).toBe(true);
    c.soStartDate = '';
    expect(c.soFormValid).toBe(false);

    c.extSrcAccount = acct('s') as never;
    c.extAmount = 1000;
    c.extBeneficiaryName = 'ACME';
    expect(c.extFormValid).toBe(true);
    c.extBeneficiaryName = '';
    expect(c.extFormValid).toBe(false);
  });

  it('display helpers: statusVariant + isCredit', () => {
    const c = TestBed.createComponent(PaymentsListComponent).componentInstance;
    expect(c.statusVariant('COMPLETED')).toBe('success');
    expect(c.statusVariant('PROCESSING')).toBe('warning');
    expect(c.statusVariant('FAILED')).toBe('error');
    expect(c.statusVariant('REVERSED')).toBe('neutral');

    c.selectedAccount = acct('a1') as never;
    expect(c.isCredit({ destinationAccountId: 'a1' } as never)).toBe(true);
    expect(c.isCredit({ destinationAccountId: 'a2' } as never)).toBe(false);
  });
});
