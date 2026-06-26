import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { AccountDetailComponent } from './account-detail';
import {
  AccountService, Account, Transaction, AccountHold, InterestCalculation, OpenAccountTemplate,
} from '../account.service';
import { CustomerService } from '../../customers/customer.service';

type AccSvc = Record<
  'get' | 'getOpenAccountTemplate' | 'create' | 'approve' | 'activate' | 'reject' |
  'freeze' | 'unfreeze' | 'close' | 'getTransactions' | 'deposit' | 'withdraw' |
  'reactivate' | 'postInterest' | 'calculateInterest' | 'getHolds' | 'placeHold' |
  'releaseHold' | 'getStatement',
  ReturnType<typeof vi.fn>
>;
type CustSvc = Record<'getImageMeta' | 'uploadImage', ReturnType<typeof vi.fn>>;

function account(over: Partial<Account> = {}): Account {
  return {
    id: 'acc-1', accountNumber: '001-SAV-0001', customerId: 'cust-1', customerName: 'Jane Doe',
    accountType: 'SAVINGS', status: 'ACTIVE', balance: 5000, currencyCode: 'USD',
    productId: 'p1', productName: 'Savings', openedDate: '2026-01-01', ...over,
  };
}
function txn(over: Partial<Transaction> = {}): Transaction {
  return {
    id: 'tx-1', accountId: 'acc-1', transactionType: 'DEPOSIT_CREDIT', amount: 100,
    runningBalance: 5100, transactionDate: '2026-06-01', ...over,
  };
}
function hold(over: Partial<AccountHold> = {}): AccountHold {
  return {
    id: 'h1', accountId: 'acc-1', amount: 200, reason: 'pending cheque',
    status: 'ACTIVE', createdAt: '2026-06-01', ...over,
  };
}
const interestCalc: InterestCalculation = {
  accountId: 'acc-1', accountNumber: '001-SAV-0001', currentBalance: 5000,
  annualInterestRate: 4, projectedDailyInterest: 0.55,
};
const template: OpenAccountTemplate = {
  depositProducts: [{ id: 'p1', name: 'Savings', interestRate: 4, minimumBalance: 100, currencyCode: 'USD' }],
  accountTypes: ['SAVINGS', 'CHECKING'],
};

function page<T>(content: T[], total = content.length) {
  return of({ content, totalElements: total, totalPages: 1, size: 20, number: 0 });
}

describe('AccountDetailComponent', () => {
  let svc: AccSvc;
  let custSvc: CustSvc;

  beforeEach(() => {
    svc = {
      get: vi.fn().mockReturnValue(of(account())),
      getOpenAccountTemplate: vi.fn().mockReturnValue(of(template)),
      create: vi.fn().mockReturnValue(of(account({ id: 'acc-new' }))),
      approve: vi.fn().mockReturnValue(of(account({ status: 'APPROVED' }))),
      activate: vi.fn().mockReturnValue(of(account({ status: 'ACTIVE' }))),
      reject: vi.fn().mockReturnValue(of(account({ status: 'REJECTED' }))),
      freeze: vi.fn().mockReturnValue(of(account({ status: 'FROZEN' }))),
      unfreeze: vi.fn().mockReturnValue(of(account({ status: 'ACTIVE' }))),
      close: vi.fn().mockReturnValue(of(account({ status: 'CLOSED' }))),
      getTransactions: vi.fn().mockReturnValue(page([txn()])),
      deposit: vi.fn().mockReturnValue(of(txn())),
      withdraw: vi.fn().mockReturnValue(of(txn({ transactionType: 'WITHDRAWAL_DEBIT' }))),
      reactivate: vi.fn().mockReturnValue(of(account({ status: 'ACTIVE' }))),
      postInterest: vi.fn().mockReturnValue(of(account({ balance: 5100 }))),
      calculateInterest: vi.fn().mockReturnValue(of(interestCalc)),
      getHolds: vi.fn().mockReturnValue(of([hold()])),
      placeHold: vi.fn().mockReturnValue(of(hold({ id: 'h2' }))),
      releaseHold: vi.fn().mockReturnValue(of(hold({ status: 'RELEASED' }))),
      getStatement: vi.fn().mockReturnValue(of({ openingBalance: 0 })),
    };
    custSvc = {
      getImageMeta: vi.fn().mockReturnValue(of({ hasImage: true })),
      uploadImage: vi.fn().mockReturnValue(of({ hasImage: true })),
    };
    configure('acc-1');
  });

  function configure(routeId: string) {
    TestBed.configureTestingModule({
      imports: [AccountDetailComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AccountService, useValue: svc },
        { provide: CustomerService, useValue: custSvc },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => routeId } } } },
      ],
    });
  }

  function make() {
    const fixture = TestBed.createComponent(AccountDetailComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads the account from the route id on init and renders the overview', () => {
    const c = make();
    expect(svc.get).toHaveBeenCalledWith('acc-1');
    expect(c.account?.id).toBe('acc-1');
    expect(c.loading).toBe(false);
    expect(c.activeTab).toBe('overview');
  });

  it('sets an error when the account is not found', () => {
    svc.get.mockReturnValue(throwError(() => new Error('404')));
    const c = make();
    expect(c.account).toBeNull();
    expect(c.error).toBe('Account not found.');
    expect(c.loading).toBe(false);
  });

  it('loads the open-account template in creation mode', () => {
    TestBed.resetTestingModule();
    configure('new');
    const c = make();
    expect(svc.get).not.toHaveBeenCalled();
    expect(svc.getOpenAccountTemplate).toHaveBeenCalled();
    expect(c.isNew).toBe(true);
    expect(c.templateProducts).toHaveLength(1);
    expect(c.templateAccountTypes).toEqual(['SAVINGS', 'CHECKING']);
  });

  describe('tab navigation lazy-loads tab data once', () => {
    it('transactions tab loads paged transactions', () => {
      const c = make();
      c.setTab('transactions');
      expect(svc.getTransactions).toHaveBeenCalledWith('acc-1', 0, 20);
      expect(c.txns).toHaveLength(1);
      expect(c.txnLoaded).toBe(true);
      c.setTab('overview');
      c.setTab('transactions');
      expect(svc.getTransactions).toHaveBeenCalledTimes(1);
    });

    it('interest tab filters by INTEREST_CREDIT', () => {
      const c = make();
      c.setTab('interest');
      expect(svc.getTransactions).toHaveBeenCalledWith('acc-1', 0, 20, 'INTEREST_CREDIT');
      expect(c.intLoaded).toBe(true);
    });

    it('holds tab loads holds', () => {
      const c = make();
      c.setTab('holds');
      expect(svc.getHolds).toHaveBeenCalledWith('acc-1');
      expect(c.holds).toHaveLength(1);
      expect(c.holdsLoaded).toBe(true);
    });
  });

  describe('teller actions', () => {
    it('doTellerAction deposits when the deposit modal is active', () => {
      const c = make();
      c.openModal('deposit');
      c.tellerAmount = 100;
      c.tellerDescription = 'cash';
      c.doTellerAction();
      expect(svc.deposit).toHaveBeenCalledWith('acc-1', 100, 'cash');
      expect(c.activeModal).toBeNull();
    });

    it('doTellerAction withdraws when the withdraw modal is active', () => {
      const c = make();
      c.openModal('withdraw');
      c.tellerAmount = 50;
      c.doTellerAction();
      expect(svc.withdraw).toHaveBeenCalledWith('acc-1', 50, undefined);
    });

    it('doTellerAction is a no-op for a non-positive amount', () => {
      const c = make();
      c.openModal('deposit');
      c.tellerAmount = 0;
      c.doTellerAction();
      expect(svc.deposit).not.toHaveBeenCalled();
    });
  });

  describe('status actions', () => {
    it('doFreeze freezes the account', () => {
      const c = make();
      c.doFreeze();
      expect(svc.freeze).toHaveBeenCalledWith('acc-1');
      expect(c.account?.status).toBe('FROZEN');
      expect(c.activeModal).toBeNull();
    });

    it('doClose navigates back on success', () => {
      const c = make();
      const router = TestBed.inject(Router);
      const navSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
      c.doClose();
      expect(svc.close).toHaveBeenCalledWith('acc-1');
      expect(navSpy).toHaveBeenCalled();
    });

    it('doClose surfaces an error on failure', () => {
      svc.close.mockReturnValue(throwError(() => new Error('balance')));
      const c = make();
      c.doClose();
      expect(c.modalError).toContain('Close failed');
      expect(c.modalWorking).toBe(false);
    });
  });

  describe('holds', () => {
    it('doPlaceHold posts a valid hold and reloads', () => {
      const c = make();
      c.holdForm = { amount: 200, reason: 'cheque' };
      c.doPlaceHold();
      expect(svc.placeHold).toHaveBeenCalledWith('acc-1', { amount: 200, reason: 'cheque' });
      expect(svc.getHolds).toHaveBeenCalled();
      expect(c.activeModal).toBeNull();
    });

    it('doPlaceHold is a no-op for an invalid form', () => {
      const c = make();
      c.holdForm = { amount: 0, reason: '' };
      c.doPlaceHold();
      expect(svc.placeHold).not.toHaveBeenCalled();
    });

    it('doReleaseHold releases the selected hold', () => {
      const c = make();
      c.holdToRelease = hold();
      c.doReleaseHold();
      expect(svc.releaseHold).toHaveBeenCalledWith('acc-1', 'h1');
      expect(c.holdToRelease).toBeNull();
    });
  });

  describe('interest', () => {
    it('openPostInterestModal fetches a preview', () => {
      const c = make();
      c.openPostInterestModal();
      expect(svc.calculateInterest).toHaveBeenCalledWith('acc-1');
      expect(c.activeModal).toBe('postInterest');
      expect(c.interestPreview?.projectedDailyInterest).toBe(0.55);
    });

    it('doPostInterest posts interest and invalidates the interest cache', () => {
      const c = make();
      c.intLoaded = true;
      c.doPostInterest();
      expect(svc.postInterest).toHaveBeenCalledWith('acc-1');
      expect(c.account?.balance).toBe(5100);
      expect(c.activeModal).toBeNull();
    });
  });

  describe('helpers', () => {
    let c: AccountDetailComponent;
    beforeEach(() => { c = make(); });

    it('statusVariant maps account statuses', () => {
      expect(c.statusVariant('ACTIVE')).toBe('success');
      expect(c.statusVariant('APPROVED')).toBe('info');
      expect(c.statusVariant('SUBMITTED')).toBe('warning');
      expect(c.statusVariant('FROZEN')).toBe('error');
      expect(c.statusVariant('REJECTED')).toBe('error');
      expect(c.statusVariant('CLOSED')).toBe('neutral');
    });

    it('holdStatusVariant maps hold statuses', () => {
      expect(c.holdStatusVariant('ACTIVE')).toBe('warning');
      expect(c.holdStatusVariant('RELEASED')).toBe('success');
      expect(c.holdStatusVariant('EXPIRED')).toBe('neutral');
    });

    it('isCredit / txnSign / txnClass reflect the transaction direction', () => {
      const credit = txn({ transactionType: 'DEPOSIT_CREDIT' });
      const debit = txn({ transactionType: 'WITHDRAWAL_DEBIT' });
      expect(c.isCredit(credit)).toBe(true);
      expect(c.isCredit(debit)).toBe(false);
      expect(c.txnSign(credit)).toBe('+');
      expect(c.txnSign(debit)).toBe('−');
      expect(c.txnClass(credit)).toBe('amount--credit');
      expect(c.txnClass(debit)).toBe('amount--debit');
    });

    it('inLockinPeriod is true when today is on or before the expiry date', () => {
      c.account = account({ lockinExpiryDate: '2999-01-01' });
      expect(c.inLockinPeriod).toBe(true);
      c.account = account({ lockinExpiryDate: '2000-01-01' });
      expect(c.inLockinPeriod).toBe(false);
      c.account = account({ lockinExpiryDate: undefined });
      expect(c.inLockinPeriod).toBe(false);
    });

    it('txn pagination getters compute the visible window', () => {
      c.txnTotal = 50;
      c.txnPage = 0;
      expect(c.txnStartRow).toBe(1);
      expect(c.txnEndRow).toBe(20);
      expect(c.txnTotalPages).toBe(3);
      c.txnTotal = 0;
      expect(c.txnStartRow).toBe(0);
    });
  });
});
