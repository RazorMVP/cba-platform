import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { TellerDetailComponent } from './teller-detail';
import {
  TellerService, Teller, Cashier, TellerSession, CashTransaction,
} from '../teller.service';

type Svc = Record<
  'get' | 'getCashiers' | 'assignCashier' | 'getSessions' | 'openSession' |
  'closeSession' | 'getSessionTransactions' | 'recordTransaction' | 'activate' | 'close',
  ReturnType<typeof vi.fn>
>;

function teller(over: Partial<Teller> = {}): Teller {
  return { id: 't1', name: 'Main', branchCode: '001', status: 'ACTIVE', startDate: '2026-01-01', ...over };
}
function cashier(over: Partial<Cashier> = {}): Cashier {
  return { id: 'c1', tellerId: 't1', staffId: 's1', startDate: '2026-01-01', fullDay: true, active: true, ...over };
}
function session(over: Partial<TellerSession> = {}): TellerSession {
  return {
    id: 'sess-1', tellerId: 't1', cashierId: 'c1', sessionDate: '2026-06-01',
    openingBalance: 500, currencyCode: 'USD', status: 'OPEN', openedAt: '2026-06-01T08:00:00Z', ...over,
  };
}
function txn(over: Partial<CashTransaction> = {}): CashTransaction {
  return {
    id: 'tx-1', sessionId: 'sess-1', tellerId: 't1', cashierId: 'c1',
    transactionType: 'CASH_IN', amount: 100, currencyCode: 'USD',
    referenceNumber: 'R1', transactionDate: '2026-06-01', ...over,
  };
}

describe('TellerDetailComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      get: vi.fn().mockReturnValue(of(teller())),
      getCashiers: vi.fn().mockReturnValue(of([cashier()])),
      assignCashier: vi.fn().mockReturnValue(of(cashier({ id: 'c2', staffId: 's2' }))),
      getSessions: vi.fn().mockReturnValue(of([session()])),
      openSession: vi.fn().mockReturnValue(of(session({ id: 'sess-2' }))),
      closeSession: vi.fn().mockReturnValue(of(session({ id: 'sess-1', status: 'CLOSED', actualCash: 600 }))),
      getSessionTransactions: vi.fn().mockReturnValue(of([txn()])),
      recordTransaction: vi.fn().mockReturnValue(of(txn({ id: 'tx-2' }))),
      activate: vi.fn().mockReturnValue(of(teller({ status: 'ACTIVE' }))),
      close: vi.fn().mockReturnValue(of(teller({ status: 'CLOSED' }))),
    };
    TestBed.configureTestingModule({
      imports: [TellerDetailComponent],
      providers: [
        provideRouter([]),
        { provide: TellerService, useValue: svc },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 't1' } } } },
      ],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(TellerDetailComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads the teller and cashiers from the route id on init', () => {
    const c = make();
    expect(svc.get).toHaveBeenCalledWith('t1');
    expect(svc.getCashiers).toHaveBeenCalledWith('t1');
    expect(c.teller?.id).toBe('t1');
    expect(c.cashiers).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('sets an error when the teller is not found', () => {
    svc.get.mockReturnValue(throwError(() => new Error('404')));
    const c = make();
    expect(c.teller).toBeNull();
    expect(c.error).toBe('Teller not found.');
    expect(c.loading).toBe(false);
  });

  describe('tab navigation', () => {
    it('switching to sessions lazy-loads sessions sorted by date desc', () => {
      svc.getSessions.mockReturnValue(of([
        session({ id: 'old', sessionDate: '2026-05-01' }),
        session({ id: 'new', sessionDate: '2026-06-01' }),
      ]));
      const c = make();
      c.setTab('sessions');
      expect(c.activeTab).toBe('sessions');
      expect(svc.getSessions).toHaveBeenCalledWith('t1');
      expect(c.sessions.map(s => s.id)).toEqual(['new', 'old']);
      expect(c.sessionsLoaded).toBe(true);
    });

    it('does not reload sessions once loaded', () => {
      const c = make();
      c.setTab('sessions');
      c.setTab('overview');
      c.setTab('sessions');
      expect(svc.getSessions).toHaveBeenCalledTimes(1);
    });
  });

  describe('session selection', () => {
    it('selecting a session loads its transactions', () => {
      const c = make();
      c.selectSession(session());
      expect(c.selectedSession?.id).toBe('sess-1');
      expect(svc.getSessionTransactions).toHaveBeenCalledWith('t1', 'sess-1');
      expect(c.sessionTxns).toHaveLength(1);
      expect(c.sessionTxnsLoading).toBe(false);
    });

    it('re-selecting the same session collapses it', () => {
      const c = make();
      const s = session();
      c.selectSession(s);
      c.selectSession(s);
      expect(c.selectedSession).toBeNull();
      expect(c.sessionTxns).toHaveLength(0);
    });

    it('openSession getter returns the first OPEN session', () => {
      const c = make();
      c.sessions = [session({ id: 'closed', status: 'CLOSED' }), session({ id: 'live', status: 'OPEN' })];
      expect(c.openSession?.id).toBe('live');
    });
  });

  describe('running balance', () => {
    it('sums cash-in minus cash-out on top of the opening balance', () => {
      const c = make();
      c.selectedSession = session({ openingBalance: 500 });
      c.sessionTxns = [
        txn({ transactionType: 'CASH_IN', amount: 200 }),
        txn({ transactionType: 'CASH_OUT', amount: 50 }),
      ];
      expect(c.sessionRunningBalance).toBe(650);
    });

    it('returns 0 without a selected session', () => {
      const c = make();
      c.selectedSession = null;
      expect(c.sessionRunningBalance).toBe(0);
    });
  });

  describe('cash transaction modal', () => {
    it('openCashTxnModal sets the type and resets the form', () => {
      const c = make();
      c.openCashTxnModal('CASH_OUT');
      expect(c.activeModal).toBe('cash-txn');
      expect(c.cashTxnType).toBe('CASH_OUT');
      expect(c.cashTxnAmount).toBe(0);
    });

    it('submitCashTxn records the transaction against the selected session', () => {
      const c = make();
      c.selectedSession = session();
      c.sessionTxns = [];
      c.openCashTxnModal('CASH_IN');
      c.cashTxnAmount = 100;
      c.submitCashTxn();
      expect(svc.recordTransaction).toHaveBeenCalledWith('t1', 'sess-1', expect.objectContaining({
        transactionType: 'CASH_IN', amount: 100, currencyCode: 'USD',
      }));
      expect(c.sessionTxns.map(t => t.id)).toContain('tx-2');
      expect(c.activeModal).toBeNull();
    });

    it('submitCashTxn is a no-op without a selected session or positive amount', () => {
      const c = make();
      c.selectedSession = null;
      c.cashTxnAmount = 100;
      c.submitCashTxn();
      expect(svc.recordTransaction).not.toHaveBeenCalled();
    });
  });

  describe('settle modal', () => {
    it('openSettleModal seeds actual cash from the running balance', () => {
      const c = make();
      c.selectedSession = session({ openingBalance: 500 });
      c.sessionTxns = [txn({ transactionType: 'CASH_IN', amount: 100 })];
      c.openSettleModal();
      expect(c.activeModal).toBe('settle');
      expect(c.settleActualCash).toBe(600);
    });

    it('submitSettle closes the session and updates state', () => {
      const c = make();
      c.sessions = [session()];
      c.selectedSession = session();
      c.settleActualCash = 600;
      c.submitSettle();
      expect(svc.closeSession).toHaveBeenCalledWith('t1', 'sess-1', expect.objectContaining({ actualCash: 600 }));
      expect(c.selectedSession?.status).toBe('CLOSED');
      expect(c.activeModal).toBeNull();
    });
  });

  describe('teller lifecycle', () => {
    it('submitActivate activates the teller', () => {
      const c = make();
      c.submitActivate();
      expect(svc.activate).toHaveBeenCalledWith('t1');
      expect(c.activeModal).toBeNull();
    });

    it('submitCloseTeller surfaces an error on failure', () => {
      svc.close.mockReturnValue(throwError(() => new Error('open session')));
      const c = make();
      c.submitCloseTeller();
      expect(c.modalError).toContain('Cannot close teller');
      expect(c.modalWorking).toBe(false);
    });
  });

  describe('display helpers', () => {
    let c: TellerDetailComponent;
    beforeEach(() => { c = make(); });

    it('tellerStatusVariant maps statuses', () => {
      expect(c.tellerStatusVariant('ACTIVE')).toBe('success');
      expect(c.tellerStatusVariant('INACTIVE')).toBe('warning');
      expect(c.tellerStatusVariant('CLOSED')).toBe('neutral');
    });

    it('sessionStatusVariant maps OPEN vs CLOSED', () => {
      expect(c.sessionStatusVariant('OPEN')).toBe('success');
      expect(c.sessionStatusVariant('CLOSED')).toBe('neutral');
    });

    it('txnSign and txnClass reflect the direction', () => {
      expect(c.txnSign(txn({ transactionType: 'CASH_IN' }))).toBe('+');
      expect(c.txnSign(txn({ transactionType: 'CASH_OUT' }))).toBe('−');
      expect(c.txnClass(txn({ transactionType: 'CASH_IN' }))).toBe('amount--in');
      expect(c.txnClass(txn({ transactionType: 'CASH_OUT' }))).toBe('amount--out');
    });
  });
});
