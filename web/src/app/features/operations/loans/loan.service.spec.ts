import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ApiService } from '../../../core/api/api.service';
import { LoanService } from './loan.service';

describe('LoanService', () => {
  let service: LoanService;
  let api: Record<'get' | 'getPage' | 'post' | 'delete' | 'command', ReturnType<typeof vi.fn>>;

  beforeEach(() => {
    api = {
      get: vi.fn().mockReturnValue(of({})),
      getPage: vi.fn().mockReturnValue(of({ content: [] })),
      post: vi.fn().mockReturnValue(of({})),
      delete: vi.fn().mockReturnValue(of({})),
      command: vi.fn().mockReturnValue(of({})),
    };
    TestBed.configureTestingModule({
      providers: [LoanService, { provide: ApiService, useValue: api }],
    });
    service = TestBed.inject(LoanService);
  });

  it('list() pages /loans with status + customerId filters', () => {
    service.list(0, 20, 'ACTIVE', 'c1').subscribe();
    expect(api.getPage).toHaveBeenCalledWith('/loans', 0, 20, { status: 'ACTIVE', customerId: 'c1' });
  });

  it('get() and getSchedule() route correctly', () => {
    service.get('l1').subscribe();
    expect(api.get).toHaveBeenCalledWith('/loans/l1');
    service.getSchedule('l1').subscribe();
    expect(api.get).toHaveBeenCalledWith('/loans/l1/repayment-schedule');
  });

  it('create() posts the loan body', () => {
    const body = { customerId: 'c1', productId: 'p1', principalAmount: 1000, termMonths: 12 };
    service.create(body).subscribe();
    expect(api.post).toHaveBeenCalledWith('/loans', body);
  });

  describe('lifecycle commands (Mifos command pattern)', () => {
    it('approve() sends the approved amount', () => {
      service.approve('l1', 900).subscribe();
      expect(api.command).toHaveBeenCalledWith('/loans/l1', 'approve', { approvedAmount: 900 });
    });

    it('disburse() sends no body', () => {
      service.disburse('l1').subscribe();
      expect(api.command).toHaveBeenCalledWith('/loans/l1', 'disburse');
    });

    it('reject() sends the reason', () => {
      service.reject('l1', 'incomplete').subscribe();
      expect(api.command).toHaveBeenCalledWith('/loans/l1', 'reject', { reason: 'incomplete' });
    });

    it('recordRepayment() maps to transactionAmount/transactionDate', () => {
      service.recordRepayment('l1', 250, '2026-06-01').subscribe();
      expect(api.command).toHaveBeenCalledWith('/loans/l1', 'repayment', {
        transactionAmount: 250,
        transactionDate: '2026-06-01',
      });
    });
  });

  describe('NPA / restructuring posts', () => {
    it('writeOff() posts reason + date', () => {
      service.writeOff('l1', 'default', '2026-06-01').subscribe();
      expect(api.post).toHaveBeenCalledWith('/loans/l1/write-off', {
        reason: 'default',
        writeOffDate: '2026-06-01',
      });
    });

    it('undoWriteOff / waiveInterest / foreclose route correctly', () => {
      service.undoWriteOff('l1').subscribe();
      expect(api.post).toHaveBeenCalledWith('/loans/l1/undo-write-off', {});
      service.waiveInterest('l1', 'goodwill').subscribe();
      expect(api.post).toHaveBeenCalledWith('/loans/l1/waive-interest', { reason: 'goodwill' });
      service.foreclose('l1', 'early', '2026-06-01').subscribe();
      expect(api.post).toHaveBeenCalledWith('/loans/l1/foreclose', {
        reason: 'early',
        foreclosureDate: '2026-06-01',
      });
    });
  });

  describe('charges — getPage unwrapped to .content', () => {
    it('getCharges() pages /loans/{id}/charges and returns just the content array', () => {
      api.getPage.mockReturnValue(of({ content: [{ id: 'lc1' }] }));
      let charges: unknown;
      service.getCharges('l1').subscribe(c => (charges = c));

      expect(api.getPage).toHaveBeenCalledWith('/loans/l1/charges', 0, 100);
      expect(charges).toEqual([{ id: 'lc1' }]);
    });

    it('listAvailableCharges() filters /charges by appliesTo=LOAN and returns content', () => {
      api.getPage.mockReturnValue(of({ content: [{ id: 'ch1' }] }));
      let charges: unknown;
      service.listAvailableCharges().subscribe(c => (charges = c));

      expect(api.getPage).toHaveBeenCalledWith('/charges', 0, 100, { appliesTo: 'LOAN' });
      expect(charges).toEqual([{ id: 'ch1' }]);
    });

    it('addCharge() normalises a missing dueDate to null', () => {
      service.addCharge('l1', 'def1', 50).subscribe();
      expect(api.post).toHaveBeenCalledWith('/loans/l1/charges', {
        chargeDefinitionId: 'def1',
        amount: 50,
        dueDate: null,
      });
    });

    it('payCharge / waiveCharge / deleteCharge route correctly', () => {
      service.payCharge('l1', 'lc1').subscribe();
      expect(api.post).toHaveBeenCalledWith('/loans/l1/charges/lc1/pay', {});
      service.waiveCharge('l1', 'lc1').subscribe();
      expect(api.post).toHaveBeenCalledWith('/loans/l1/charges/lc1/waive', {});
      service.deleteCharge('l1', 'lc1').subscribe();
      expect(api.delete).toHaveBeenCalledWith('/loans/l1/charges/lc1');
    });
  });

  it('getAuditLog() queries /audits scoped to the loan', () => {
    service.getAuditLog('l1').subscribe();
    expect(api.get).toHaveBeenCalledWith('/audits', { entityType: 'LOAN', entityId: 'l1' });
  });

  describe('reschedule', () => {
    it('list/create route correctly', () => {
      service.listRescheduleRequests('l1').subscribe();
      expect(api.get).toHaveBeenCalledWith('/loanreschedule', { loanId: 'l1' });
      const req = { loanId: 'l1', rescheduleReason: 'x', recalculateInterest: true };
      service.createRescheduleRequest(req).subscribe();
      expect(api.post).toHaveBeenCalledWith('/loanreschedule', req);
    });

    it('approve/reject use the command pattern', () => {
      service.approveReschedule('r1').subscribe();
      expect(api.command).toHaveBeenCalledWith('/loanreschedule/r1', 'approve');
      service.rejectReschedule('r1').subscribe();
      expect(api.command).toHaveBeenCalledWith('/loanreschedule/r1', 'reject');
    });
  });

  it('triggerReamortization() posts an empty body', () => {
    service.triggerReamortization('l1').subscribe();
    expect(api.post).toHaveBeenCalledWith('/loans/l1/reamortization', {});
  });
});
