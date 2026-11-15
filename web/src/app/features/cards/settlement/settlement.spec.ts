import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError, Subject } from 'rxjs';
import { SettlementComponent } from './settlement';
import {
  CardsService, SettlementBatch, SettlementTransmission, SchemeReportResult, SchemeSettlementReport,
  ReconciliationView, UnresolvedHold,
} from '../cards.service';

type Svc = Record<
  'listBatches' | 'listTransmissions' | 'closeBatch' | 'triggerExport'
    | 'expectedSchemeNet' | 'recordSchemeReport' | 'listSchemeReports' | 'reconciliation' | 'retryHoldRelease',
  ReturnType<typeof vi.fn>
>;

function result(over: Partial<SchemeReportResult> = {}): SchemeReportResult {
  return {
    recorded: true, repeat: false, reason: null, scheme: 'VISA', settlementDate: '2026-11-08', currency: 'USD',
    expectedNet: 149.2, reportedNet: 149.21, difference: 0.01, itemCount: 2, refundCount: 0,
    backendReference: 'CSR-VISA-20261108-USD', journalTransactionId: 'GL-1', ...over,
  };
}
function report(over: Partial<SchemeSettlementReport> = {}): SchemeSettlementReport {
  return {
    id: 'r1', scheme: 'VISA', settlementDate: '2026-11-08', currencyCode: 'USD', reportedNet: 149.21,
    expectedNet: 149.2, difference: 0.01, itemCount: 2, refundCount: 0, status: 'RECORDED',
    backendReference: 'CSR-VISA-20261108-USD', journalTransactionId: 'GL-1', createdBy: 'ops',
    createdAt: '2026-11-09', recordedAt: '2026-11-09', ...over,
  };
}

function hold(over: Partial<UnresolvedHold> = {}): UnresolvedHold {
  return {
    authorizationId: 'a1', cardId: 'c1', panLast4: '4242', mti: '0100', stan: '910001', rrn: '712391000100',
    amount: 25, currency: 'USD', holdReference: 'CARD-4e0694e34d576f6b528a5230', createdAt: '2026-11-10T10:00:00Z',
    ...over,
  };
}
function recon(over: Partial<ReconciliationView> = {}): ReconciliationView {
  return {
    from: null, to: null, maxRows: 500,
    unresolvedHolds: [hold()],
    stuckClearing: [{
      itemId: 'i1', batchId: 'b9', batchRef: 'BATCH-9', settlementDate: '2026-11-10', batchStatus: 'OPEN',
      cardId: 'c1', panLast4: '4242', scheme: 'VISA', amount: 12.5, currency: 'USD', holdReference: 'CARD-x',
      authorizationId: 'a9', claimedSince: '2026-11-10T23:57:00Z',
    }],
    countryUnknown: [{
      itemId: 'i2', batchId: 'b9', settlementDate: '2026-11-10', scheme: 'MASTERCARD', status: 'SETTLED',
      amount: 40, currency: 'NGN', authorizationId: 'a8', cardId: 'c2', panLast4: '1111',
    }],
    ...over,
  };
}

function batch(over: Partial<SettlementBatch> = {}): SettlementBatch {
  return {
    id: 'b1', batchRef: 'BATCH-1', status: 'OPEN', settlementDate: '2026-01-01',
    totalAmount: 12345, itemCount: 3, openedAt: '2026-01-01', closedAt: null, ...over,
  };
}
function transmission(over: Partial<SettlementTransmission> = {}): SettlementTransmission {
  return {
    id: 't1', batchId: 'b1', scheme: 'VISA', status: 'TRANSMITTED',
    endpoint: 'sftp://x', attemptCount: 1, lastAttemptAt: null, transmittedAt: '2026-01-01', ...over,
  };
}

describe('SettlementComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listBatches: vi.fn().mockReturnValue(of([batch()])),
      listTransmissions: vi.fn().mockReturnValue(of([transmission()])),
      closeBatch: vi.fn().mockReturnValue(of(batch({ status: 'CLOSED' }))),
      triggerExport: vi.fn().mockReturnValue(of(void 0)),
      expectedSchemeNet: vi.fn().mockReturnValue(of(result({ recorded: false, reportedNet: null, difference: null }))),
      recordSchemeReport: vi.fn().mockReturnValue(of(result())),
      listSchemeReports: vi.fn().mockReturnValue(of([report()])),
      reconciliation: vi.fn().mockReturnValue(of(recon())),
      retryHoldRelease: vi.fn().mockReturnValue(of({
        authorizationId: 'a1', holdReference: 'CARD-4e06', resolution: 'RELEASED', resolvedAt: '2026-11-13',
        resolvedBy: 'ops', backendStatus: 'RELEASED', backendReason: null,
      })),
    };
    TestBed.configureTestingModule({
      imports: [SettlementComponent],
      providers: [provideRouter([]), { provide: CardsService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(SettlementComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads batches and transmissions on init', () => {
    const c = make();
    expect(svc.listBatches).toHaveBeenCalled();
    expect(svc.listTransmissions).toHaveBeenCalled();
    expect(c.batches).toHaveLength(1);
    expect(c.transmissions).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('keeps loading false when batch load fails', () => {
    svc.listBatches.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.loading).toBe(false);
  });

  it('closeBatch triggers reload', () => {
    const c = make();
    svc.listBatches.mockClear();
    c.closeBatch('b1');
    expect(svc.closeBatch).toHaveBeenCalledWith('b1');
    expect(svc.listBatches).toHaveBeenCalled();
  });

  it('triggerExport triggers reload', () => {
    const c = make();
    svc.listBatches.mockClear();
    c.triggerExport('b1');
    expect(svc.triggerExport).toHaveBeenCalledWith('b1');
    expect(svc.listBatches).toHaveBeenCalled();
  });

  it('triggerExport shows the server refusal and clears it on the next try', () => {
    svc.triggerExport.mockReturnValue(throwError(() => ({
      error: { errors: [{ code: 'PRESENTMENT_REQUIRES_ACQUIRER_ROLE', message: 'an issuer does not send them' }] },
    })));
    const c = make();
    c.triggerExport('b1');
    expect(c.exportError).toBe('an issuer does not send them');

    svc.triggerExport.mockReturnValue(of(void 0));
    c.triggerExport('b1');
    expect(c.exportError).toBeNull();
  });

  it('toggleExpand expands then collapses the same batch', () => {
    const c = make();
    c.toggleExpand('b1');
    expect(c.expandedBatch).toBe('b1');
    c.toggleExpand('b1');
    expect(c.expandedBatch).toBeNull();
  });

  it('batchTransmissions filters by batch id', () => {
    const c = make();
    c.transmissions = [transmission({ id: 't1', batchId: 'b1' }), transmission({ id: 't2', batchId: 'b2' })];
    expect(c.batchTransmissions('b1').map(t => t.id)).toEqual(['t1']);
  });

  it('statusVariant maps batch + transmission statuses', () => {
    const c = make();
    expect(c.statusVariant('OPEN')).toBe('info');
    expect(c.statusVariant('CLOSED')).toBe('warning');
    expect(c.statusVariant('SETTLED')).toBe('success');
    expect(c.statusVariant('FAILED')).toBe('error');
    expect(c.statusVariant('TRANSMITTED')).toBe('success');
    expect(c.statusVariant('SENDING')).toBe('info');
    expect(c.statusVariant('ZZZ')).toBe('neutral');
  });

  describe('Scheme Reports tab', () => {
    it('opening the tab loads the reports for the list date and renders expected, reported and difference', () => {
      const fixture = TestBed.createComponent(SettlementComponent);
      fixture.detectChanges();
      const c = fixture.componentInstance;
      c.setTab('reports');
      fixture.detectChanges();
      expect(svc.listSchemeReports).toHaveBeenCalledWith(c.reportsDate);
      expect(c.reports).toHaveLength(1);
      const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
      expect(text).toContain('149.20');
      expect(text).toContain('149.21');
      expect(text).toContain('+0.01');
    });

    it('lookupExpected shows the expected net, items and refunds', () => {
      const c = make();
      c.reportForm = { scheme: 'MASTERCARD', settlementDate: '2026-11-07', currency: 'NGN', netAmount: null };
      c.lookupExpected();
      expect(svc.expectedSchemeNet).toHaveBeenCalledWith('MASTERCARD', '2026-11-07', 'NGN');
      expect(c.expected?.expectedNet).toBe(149.2);
      expect(c.expectedError).toBeNull();
    });

    it('lookupExpected shows a refusal reason (e.g. unknown currency) as an error', () => {
      svc.expectedSchemeNet.mockReturnValue(of(result({ reason: 'UNKNOWN_CURRENCY' })));
      const c = make();
      c.lookupExpected();
      expect(c.expectedError).toBe('Unknown currency.');
    });

    it('recordReport needs an amount', () => {
      const c = make();
      c.recordReport();
      expect(c.recordError).toContain('net amount');
      expect(svc.recordSchemeReport).not.toHaveBeenCalled();
    });

    it('recordReport sends the form, shows the result and reloads the list for that date', () => {
      const c = make();
      c.reportForm = { scheme: 'VISA', settlementDate: '2026-11-08', currency: 'USD', netAmount: 149.21 };
      c.recordReport();
      expect(svc.recordSchemeReport).toHaveBeenCalledWith(
        { scheme: 'VISA', settlementDate: '2026-11-08', currency: 'USD', netAmount: 149.21 });
      expect(c.recordResult?.recorded).toBe(true);
      expect(c.reportsDate).toBe('2026-11-08');
      expect(svc.listSchemeReports).toHaveBeenCalledWith('2026-11-08');
      expect(c.recording).toBe(false);
    });

    it('a refusal over tolerance is shown with the figures and the reason in plain words', () => {
      svc.recordSchemeReport.mockReturnValue(of(result({
        recorded: false, reason: 'DIFFERENCE_OVER_TOLERANCE', reportedNet: 149.3, difference: 0.1,
        backendReference: null, journalTransactionId: null,
      })));
      const fixture = TestBed.createComponent(SettlementComponent);
      fixture.detectChanges();
      const c = fixture.componentInstance;
      c.setTab('reports');
      c.reportForm.netAmount = 149.3;
      c.recordReport();
      fixture.detectChanges();
      const el = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="record-result"]');
      expect(el?.textContent).toContain('DIFFERENCE_OVER_TOLERANCE');
      expect(el?.textContent).toContain('above the tolerance');
      expect(el?.textContent).toContain('+0.10');
      expect(el?.textContent).toContain('149.30');
    });

    it('an HTTP error while recording shows the server message', () => {
      svc.recordSchemeReport.mockReturnValue(throwError(() => ({
        error: { errors: [{ code: 'ACCESS_DENIED', message: 'not allowed' }] },
      })));
      const c = make();
      c.reportForm.netAmount = 1;
      c.recordReport();
      expect(c.recordError).toBe('not allowed');
      expect(c.recording).toBe(false);
    });

    it('a failed list load leaves an empty list', () => {
      svc.listSchemeReports.mockReturnValue(throwError(() => new Error('x')));
      const c = make();
      c.loadReports();
      expect(c.reports).toEqual([]);
      expect(c.reportsLoading).toBe(false);
    });

    it('amount helpers format signed figures', () => {
      const c = make();
      expect(c.amount(null)).toBe('—');
      expect(c.amount(-39.45)).toBe('-39.45');
      expect(c.signedAmount(0.01)).toBe('+0.01');
      expect(c.signedAmount(-0.05)).toBe('-0.05');
      expect(c.signedAmount(0)).toBe('0.00');
      expect(c.reasonText('SOMETHING_NEW')).toBe('SOMETHING_NEW');
      expect(c.statusVariant('RECORDED')).toBe('success');
      expect(c.statusVariant('POSTING')).toBe('warning');
    });
  });

  describe('Reconciliation tab', () => {
    function open() {
      const fixture = TestBed.createComponent(SettlementComponent);
      fixture.detectChanges();
      const c = fixture.componentInstance;
      c.setTab('reconciliation');
      fixture.detectChanges();
      return { fixture, c, el: fixture.nativeElement as HTMLElement };
    }

    it('opening the tab loads all three lists and renders them (last four only)', () => {
      const { c, el } = open();
      expect(svc.reconciliation).toHaveBeenCalledWith(undefined, undefined);
      expect(c.recon?.unresolvedHolds).toHaveLength(1);
      expect(el.querySelector('[data-testid="recon-holds"]')?.textContent).toContain('•••• 4242');
      expect(el.querySelector('[data-testid="recon-holds"]')?.textContent).toContain('25.00 USD');
      expect(el.querySelector('[data-testid="recon-clearing"]')?.textContent).toContain('BATCH-9');
      expect(el.querySelector('[data-testid="recon-clearing"]')?.textContent).toContain('Closing the batch again retries');
      expect(el.querySelector('[data-testid="recon-country"]')?.textContent).toContain('40.00 NGN');
    });

    it('the date filter is sent when set', () => {
      const { c } = open();
      c.reconFrom = '2026-11-01';
      c.reconTo = '2026-11-12';
      c.loadReconciliation();
      expect(svc.reconciliation).toHaveBeenLastCalledWith('2026-11-01', '2026-11-12');
    });

    it('empty lists show empty states', () => {
      svc.reconciliation.mockReturnValue(of(recon({ unresolvedHolds: [], stuckClearing: [], countryUnknown: [] })));
      const { el } = open();
      expect(el.textContent).toContain('No unresolved holds.');
      expect(el.textContent).toContain('Nothing stuck in clearing.');
      expect(el.textContent).toContain('No items without a country.');
    });

    it('a failed load shows the error banner', () => {
      svc.reconciliation.mockReturnValue(throwError(() => ({
        error: { errors: [{ code: 'INVALID_DATE_RANGE', message: 'from must not be after to' }] },
      })));
      const { c, el } = open();
      expect(c.reconError).toBe('from must not be after to');
      expect(el.querySelector('.error-banner')?.textContent).toContain('from must not be after to');
      expect(c.reconLoading).toBe(false);
    });

    it('Retry release shows what the release came to and replaces the button', () => {
      const { fixture, c, el } = open();
      (el.querySelector('[data-testid="retry-a1"]') as HTMLButtonElement).click();
      fixture.detectChanges();
      expect(svc.retryHoldRelease).toHaveBeenCalledWith('a1');
      expect(c.releaseResult['a1']).toContain('Released');
      expect(el.querySelector('[data-testid="retry-a1"]')).toBeNull();
      expect(el.querySelector('[data-testid="hold-a1"]')?.textContent).toContain('RESOLVED');
      // A resolved hold isn't sent again.
      c.retryRelease(hold());
      expect(svc.retryHoldRelease).toHaveBeenCalledTimes(1);
    });

    it('a tombstoned reference is explained in plain words', () => {
      svc.retryHoldRelease.mockReturnValue(of({
        authorizationId: 'a1', holdReference: 'x', resolution: 'NOT_FOUND_TOMBSTONED', resolvedAt: '', resolvedBy: 'ops',
        backendStatus: null, backendReason: 'HOLD_NOT_FOUND',
      }));
      const { c } = open();
      c.retryRelease(hold());
      expect(c.releaseResult['a1']).toContain('never placed');
    });

    it('the button is disabled while the release is in flight', () => {
      const pending = new Subject<never>();
      svc.retryHoldRelease.mockReturnValue(pending.asObservable());
      const { fixture, c, el } = open();
      c.retryRelease(hold());
      fixture.detectChanges();
      const btn = el.querySelector('[data-testid="retry-a1"]') as HTMLButtonElement;
      expect(btn.disabled).toBe(true);
      expect(btn.textContent).toContain('Releasing');
      c.retryRelease(hold());
      expect(svc.retryHoldRelease).toHaveBeenCalledTimes(1);
    });

    it('an outage or refusal is shown in plain words and the button stays for another try', () => {
      svc.retryHoldRelease.mockReturnValue(throwError(() => ({
        status: 503, error: { errors: [{ code: 'BACKEND_UNAVAILABLE', message: 'unreachable' }] },
      })));
      const { fixture, c, el } = open();
      c.retryRelease(hold());
      fixture.detectChanges();
      expect(c.releaseError['a1']).toContain('could not be reached');
      expect(el.querySelector('[data-testid="retry-a1"]')).not.toBeNull();
      expect(c.releasing['a1']).toBe(false);

      svc.retryHoldRelease.mockReturnValue(throwError(() => ({
        status: 409, error: { errors: [{ code: 'ACCOUNT_CLOSED', message: 'refused: ACCOUNT_CLOSED' }] },
      })));
      c.retryRelease(hold());
      expect(c.releaseError['a1']).toContain('account is closed');

      svc.retryHoldRelease.mockReturnValue(throwError(() => ({
        status: 409, error: { errors: [{ code: 'SOMETHING_NEW', message: 'refused: SOMETHING_NEW' }] },
      })));
      c.retryRelease(hold());
      expect(c.releaseError['a1']).toBe('refused: SOMETHING_NEW');
    });

    it('a stuck item links to its batch on the Batches tab', () => {
      const { fixture, c, el } = open();
      (el.querySelector('[data-testid="batch-link-i1"]') as HTMLButtonElement).click();
      fixture.detectChanges();
      expect(c.activeTab).toBe('batches');
      expect(c.expandedBatch).toBe('b9');
      expect(c.statusVariant('CLEARING')).toBe('warning');
    });
  });

  it('totalAmount renders cents as a 2dp string', () => {
    const c = make();
    expect(c.totalAmount(batch({ totalAmount: 12345 }))).toBe('123.45');
  });
});
