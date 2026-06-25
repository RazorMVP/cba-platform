import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { DisputesComponent } from './disputes';
import { CardsService, CardDispute, ChargebackReasonCode } from '../cards.service';

type Svc = Record<
  'listDisputes' | 'listReasonCodes' | 'raiseDispute' | 'resolveDispute' | 'disputeCommand',
  ReturnType<typeof vi.fn>
>;

function dispute(over: Partial<CardDispute> = {}): CardDispute {
  return {
    id: 'd1', cardId: 'card-1', transactionRef: 'rrn1', disputeReason: 'UNAUTHORIZED',
    status: 'RAISED', raisedBy: 'cust-1', resolvedBy: null, originalAmount: 1000,
    resolutionNotes: null, schemeReasonCode: null, chargebackDeadline: null,
    responseDeadline: null, createdAt: '2026-01-01', ...over,
  };
}
const reasonCode: ChargebackReasonCode = { id: 'rc1', scheme: 'VISA', code: '10.4', description: 'Fraud', maxDaysToRespond: 45 };

describe('DisputesComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      listDisputes: vi.fn().mockReturnValue(of([dispute()])),
      listReasonCodes: vi.fn().mockReturnValue(of([reasonCode])),
      raiseDispute: vi.fn().mockReturnValue(of(dispute({ id: 'd2' }))),
      resolveDispute: vi.fn().mockReturnValue(of(dispute({ status: 'RESOLVED' }))),
      disputeCommand: vi.fn().mockReturnValue(of(dispute({ status: 'CHARGEBACK_INITIATED' }))),
    };
    TestBed.configureTestingModule({
      imports: [DisputesComponent],
      providers: [provideRouter([]), { provide: CardsService, useValue: svc }],
    });
  });

  function make() {
    const fixture = TestBed.createComponent(DisputesComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads disputes (no status filter) and reason codes on init', () => {
    const c = make();
    expect(svc.listDisputes).toHaveBeenCalledWith(undefined);
    expect(svc.listReasonCodes).toHaveBeenCalled();
    expect(c.disputes).toHaveLength(1);
    expect(c.reasonCodes).toHaveLength(1);
    expect(c.loading).toBe(false);
  });

  it('passes the active status filter through to load', () => {
    const c = make();
    c.statusFilter = 'CHARGEBACK_INITIATED';
    c.load();
    expect(svc.listDisputes).toHaveBeenLastCalledWith('CHARGEBACK_INITIATED');
  });

  it('keeps loading false when load fails', () => {
    svc.listDisputes.mockReturnValue(throwError(() => new Error('x')));
    const c = make();
    expect(c.loading).toBe(false);
  });

  it('select / closeDetail manage the slide-in panel', () => {
    const c = make();
    c.select(dispute({ id: 'd9' }));
    expect(c.selectedDispute?.id).toBe('d9');
    c.closeDetail();
    expect(c.selectedDispute).toBeNull();
  });

  it('raiseDispute submits, closes the modal and reloads', () => {
    const c = make();
    c.showRaiseModal = true;
    c.raiseForm = { cardId: 'card-1', transactionRef: 'rrn1', disputeReason: 'DUPLICATE', raisedBy: 'cust-1', originalAmount: 500, currencyCode: 'USD' };
    svc.listDisputes.mockClear();
    c.raiseDispute();
    expect(svc.raiseDispute).toHaveBeenCalledWith(c.raiseForm);
    expect(c.showRaiseModal).toBe(false);
    expect(svc.listDisputes).toHaveBeenCalled();
  });

  describe('resolve', () => {
    it('openResolve selects the dispute and resets the resolve form', () => {
      const c = make();
      c.openResolve(dispute({ id: 'd1' }));
      expect(c.selectedDispute?.id).toBe('d1');
      expect(c.showResolveModal).toBe(true);
      expect(c.resolveForm).toEqual({ resolvedBy: '', resolutionFavor: 'ISSUER', notes: '' });
    });

    it('submitResolve resolves, closes modal + detail and reloads', () => {
      const c = make();
      c.openResolve(dispute({ id: 'd1' }));
      c.resolveForm = { resolvedBy: 'staff-1', resolutionFavor: 'ACQUIRER', notes: 'ok' };
      svc.listDisputes.mockClear();
      c.submitResolve();
      expect(svc.resolveDispute).toHaveBeenCalledWith('d1', c.resolveForm);
      expect(c.showResolveModal).toBe(false);
      expect(c.selectedDispute).toBeNull();
      expect(svc.listDisputes).toHaveBeenCalled();
    });

    it('submitResolve is a no-op without a selected dispute', () => {
      const c = make();
      c.selectedDispute = null;
      c.submitResolve();
      expect(svc.resolveDispute).not.toHaveBeenCalled();
    });
  });

  describe('advanceDispute (7-state chargeback workflow)', () => {
    it('replaces the dispute in place and updates the selected panel', () => {
      const c = make();
      c.disputes = [dispute({ id: 'd1', status: 'RAISED' })];
      c.selectedDispute = c.disputes[0];
      c.advanceDispute(c.disputes[0], 'chargeback');
      expect(svc.disputeCommand).toHaveBeenCalledWith('d1', 'chargeback');
      expect(c.disputes[0].status).toBe('CHARGEBACK_INITIATED');
      expect(c.selectedDispute?.status).toBe('CHARGEBACK_INITIATED');
    });

    it('leaves the selected panel untouched when a different dispute advances', () => {
      const c = make();
      c.disputes = [dispute({ id: 'd1' }), dispute({ id: 'd2' })];
      c.selectedDispute = dispute({ id: 'd2' });
      c.advanceDispute(c.disputes[0], 'retrieval');
      expect(c.selectedDispute?.id).toBe('d2');
      expect(c.selectedDispute?.status).toBe('RAISED');
    });
  });

  it('statusVariant maps the full 7-state machine', () => {
    const c = make();
    expect(c.statusVariant('RAISED')).toBe('warning');
    expect(c.statusVariant('RETRIEVAL_REQUESTED')).toBe('info');
    expect(c.statusVariant('CHARGEBACK_INITIATED')).toBe('warning');
    expect(c.statusVariant('REPRESENTMENT')).toBe('info');
    expect(c.statusVariant('PRE_ARBITRATION')).toBe('error');
    expect(c.statusVariant('RESOLVED')).toBe('success');
    expect(c.statusVariant('WITHDRAWN')).toBe('neutral');
  });
});
