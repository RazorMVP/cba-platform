import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { LoanDetailComponent } from './loan-detail';
import {
  LoanService, Loan, RepaymentInstallment, LoanCharge, AvailableCharge,
} from '../loan.service';

type Svc = Record<
  'get' | 'getSchedule' | 'create' | 'approve' | 'disburse' | 'reject' |
  'writeOff' | 'undoWriteOff' | 'waiveInterest' | 'foreclose' | 'recordRepayment' |
  'getCharges' | 'addCharge' | 'payCharge' | 'waiveCharge' | 'deleteCharge' |
  'listAvailableCharges' | 'getGuarantors' | 'getCollateral' | 'getNotes' | 'addNote' |
  'getDocuments' | 'getAuditLog' | 'listRescheduleRequests' | 'createRescheduleRequest' |
  'approveReschedule' | 'rejectReschedule' | 'listReaging' | 'createReaging' | 'triggerReamortization',
  ReturnType<typeof vi.fn>
>;

function loan(over: Partial<Loan> = {}): Loan {
  return {
    id: 'loan-1', loanAccountNumber: 'L0001', customerId: 'cust-1', customerName: 'Jane Doe',
    productId: 'p1', productName: 'Personal Loan', principalAmount: 10000, outstandingBalance: 4000,
    interestRate: 12, termMonths: 12, status: 'ACTIVE', createdAt: '2026-01-01', ...over,
  };
}
function installment(over: Partial<RepaymentInstallment> = {}): RepaymentInstallment {
  return {
    id: 'i1', loanId: 'loan-1', dueDate: '2026-02-01', principalDue: 800, interestDue: 100,
    totalDue: 900, principalPaid: 0, interestPaid: 0, status: 'PENDING', ...over,
  };
}
function charge(over: Partial<LoanCharge> = {}): LoanCharge {
  return {
    id: 'ch1', name: 'Late Fee', chargeTimeType: 'SPECIFIED_DUE_DATE', chargeCalculation: 'FLAT',
    currencyCode: 'USD', amount: 50, amountPaid: 0, amountWaived: 0, amountOutstanding: 50,
    paid: false, waived: false, ...over,
  };
}
const availCharge: AvailableCharge = {
  id: 'def1', name: 'Late Fee', amount: 50, chargeCalculation: 'FLAT', chargeTimeType: 'SPECIFIED_DUE_DATE',
};

describe('LoanDetailComponent', () => {
  let svc: Svc;

  beforeEach(() => {
    svc = {
      get: vi.fn().mockReturnValue(of(loan())),
      getSchedule: vi.fn().mockReturnValue(of([installment()])),
      create: vi.fn().mockReturnValue(of(loan({ id: 'loan-new' }))),
      approve: vi.fn().mockReturnValue(of(loan({ status: 'APPROVED' }))),
      disburse: vi.fn().mockReturnValue(of(loan({ status: 'DISBURSED' }))),
      reject: vi.fn().mockReturnValue(of(loan({ status: 'REJECTED' }))),
      writeOff: vi.fn().mockReturnValue(of(loan({ status: 'WRITTEN_OFF' }))),
      undoWriteOff: vi.fn().mockReturnValue(of(loan({ status: 'ACTIVE' }))),
      waiveInterest: vi.fn().mockReturnValue(of(loan())),
      foreclose: vi.fn().mockReturnValue(of(loan({ status: 'FORECLOSED' }))),
      recordRepayment: vi.fn().mockReturnValue(of(loan({ outstandingBalance: 3000 }))),
      getCharges: vi.fn().mockReturnValue(of([charge()])),
      addCharge: vi.fn().mockReturnValue(of(charge({ id: 'ch2' }))),
      payCharge: vi.fn().mockReturnValue(of(charge({ id: 'ch1', amountOutstanding: 0, paid: true }))),
      waiveCharge: vi.fn().mockReturnValue(of(charge({ id: 'ch1', waived: true, amountOutstanding: 0 }))),
      deleteCharge: vi.fn().mockReturnValue(of(undefined)),
      listAvailableCharges: vi.fn().mockReturnValue(of([availCharge])),
      getGuarantors: vi.fn().mockReturnValue(of([])),
      getCollateral: vi.fn().mockReturnValue(of([])),
      getNotes: vi.fn().mockReturnValue(of([])),
      addNote: vi.fn().mockReturnValue(of({ id: 'n1', note: 'hi', createdBy: 'admin', createdAt: '2026-06-01' })),
      getDocuments: vi.fn().mockReturnValue(of([])),
      getAuditLog: vi.fn().mockReturnValue(of([])),
      listRescheduleRequests: vi.fn().mockReturnValue(of([])),
      createRescheduleRequest: vi.fn().mockReturnValue(of({ id: 'rs1', status: 'PENDING' })),
      approveReschedule: vi.fn().mockReturnValue(of({ id: 'rs1', status: 'APPROVED' })),
      rejectReschedule: vi.fn().mockReturnValue(of({ id: 'rs1', status: 'REJECTED' })),
      listReaging: vi.fn().mockReturnValue(of([])),
      createReaging: vi.fn().mockReturnValue(of({ id: 'ra1' })),
      triggerReamortization: vi.fn().mockReturnValue(of(undefined)),
    };
    configure('loan-1');
  });

  function configure(routeId: string) {
    TestBed.configureTestingModule({
      imports: [LoanDetailComponent],
      providers: [
        provideRouter([]),
        { provide: LoanService, useValue: svc },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => routeId } } } },
      ],
    });
  }

  function make() {
    const fixture = TestBed.createComponent(LoanDetailComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads the loan from the route id on init and renders the summary', () => {
    const c = make();
    expect(svc.get).toHaveBeenCalledWith('loan-1');
    expect(c.loan?.id).toBe('loan-1');
    expect(c.loading).toBe(false);
    expect(c.activeTab).toBe('summary');
  });

  it('eager-loads the schedule when the loan is IN_ARREARS', () => {
    svc.get.mockReturnValue(of(loan({ status: 'IN_ARREARS' })));
    const c = make();
    expect(svc.getSchedule).toHaveBeenCalledWith('loan-1');
    expect(c.schedule).toHaveLength(1);
    expect(c.scheduleLoaded).toBe(true);
  });

  it('enters creation mode for the "new" route without loading a loan', () => {
    TestBed.resetTestingModule();
    configure('new');
    const c = make();
    expect(svc.get).not.toHaveBeenCalled();
    expect(c.isNew).toBe(true);
    expect(c.loading).toBe(false);
  });

  describe('tab navigation lazy-loads tab data once', () => {
    it('charges tab loads charges', () => {
      const c = make();
      c.selectTab('charges');
      expect(svc.getCharges).toHaveBeenCalledWith('loan-1');
      expect(c.charges).toHaveLength(1);
      expect(c.chargesLoaded).toBe(true);
      c.selectTab('summary');
      c.selectTab('charges');
      expect(svc.getCharges).toHaveBeenCalledTimes(1);
    });

    it('collateral tab loads guarantors and collateral', () => {
      const c = make();
      c.selectTab('collateral');
      expect(svc.getGuarantors).toHaveBeenCalledWith('loan-1');
      expect(svc.getCollateral).toHaveBeenCalledWith('loan-1');
      expect(c.collateralLoaded).toBe(true);
    });

    it('schedule tab loads the repayment schedule', () => {
      const c = make();
      c.selectTab('schedule');
      expect(svc.getSchedule).toHaveBeenCalledWith('loan-1');
      expect(c.schedule).toHaveLength(1);
    });
  });

  describe('lifecycle actions', () => {
    it('submitApprove approves with the optional amount', () => {
      const c = make();
      c.approveAmount = 9000;
      c.submitApprove();
      expect(svc.approve).toHaveBeenCalledWith('loan-1', 9000);
      expect(c.loan?.status).toBe('APPROVED');
      expect(c.showApproveModal).toBe(false);
    });

    it('submitDisburse disburses the loan', () => {
      const c = make();
      c.submitDisburse();
      expect(svc.disburse).toHaveBeenCalledWith('loan-1');
      expect(c.loan?.status).toBe('DISBURSED');
    });

    it('submitReject requires a reason', () => {
      const c = make();
      c.rejectReason = '   ';
      c.submitReject();
      expect(svc.reject).not.toHaveBeenCalled();
      c.rejectReason = 'duplicate';
      c.submitReject();
      expect(svc.reject).toHaveBeenCalledWith('loan-1', 'duplicate');
      expect(c.loan?.status).toBe('REJECTED');
    });

    it('submitRepayment records the repayment and invalidates the schedule cache', () => {
      const c = make();
      c.scheduleLoaded = true;
      c.repaymentAmount = 1000;
      c.submitRepayment();
      expect(svc.recordRepayment).toHaveBeenCalledWith('loan-1', 1000, c.repaymentDate);
      expect(c.loan?.outstandingBalance).toBe(3000);
      expect(c.scheduleLoaded).toBe(false);
      expect(c.showRepaymentModal).toBe(false);
    });

    it('submitWriteOff surfaces an error on failure', () => {
      svc.writeOff.mockReturnValue(throwError(() => new Error('boom')));
      const c = make();
      c.writeOffReason = 'uncollectible';
      c.submitWriteOff();
      expect(c.writeOffError).toContain('Write-off failed');
      expect(c.writeOffSaving).toBe(false);
    });
  });

  describe('charge actions', () => {
    it('openAddCharge loads available charges only once', () => {
      const c = make();
      c.openAddCharge();
      expect(svc.listAvailableCharges).toHaveBeenCalledTimes(1);
      expect(c.showAddChargeModal).toBe(true);
      c.openAddCharge();
      expect(svc.listAvailableCharges).toHaveBeenCalledTimes(1);
    });

    it('onAddChargeDefChange copies the definition default amount', () => {
      const c = make();
      c.availableCharges = [availCharge];
      c.addChargeDefinitionId = 'def1';
      c.onAddChargeDefChange();
      expect(c.addChargeAmount).toBe(50);
    });

    it('submitAddCharge appends the new charge', () => {
      const c = make();
      c.charges = [charge()];
      c.addChargeDefinitionId = 'def1';
      c.addChargeAmount = 50;
      c.submitAddCharge();
      expect(svc.addCharge).toHaveBeenCalledWith('loan-1', 'def1', 50, undefined);
      expect(c.charges.map(x => x.id)).toContain('ch2');
      expect(c.showAddChargeModal).toBe(false);
    });

    it('confirmWaiveCharge replaces the charge with the waived version', () => {
      const c = make();
      c.charges = [charge()];
      c.openWaiveCharge(charge());
      c.confirmWaiveCharge();
      expect(svc.waiveCharge).toHaveBeenCalledWith('loan-1', 'ch1');
      expect(c.charges[0].waived).toBe(true);
      expect(c.showWaiveChargeModal).toBe(false);
    });
  });

  describe('helpers', () => {
    let c: LoanDetailComponent;
    beforeEach(() => { c = make(); });

    it('repaidPct reflects the proportion paid off', () => {
      c.loan = loan({ principalAmount: 10000, outstandingBalance: 4000 });
      expect(c.repaidPct).toBe(60);
    });

    it('can* guards reflect the loan status', () => {
      c.loan = loan({ status: 'SUBMITTED' });
      expect(c.canApprove).toBe(true);
      expect(c.canReject).toBe(true);
      expect(c.canDisburse).toBe(false);

      c.loan = loan({ status: 'APPROVED' });
      expect(c.canDisburse).toBe(true);

      c.loan = loan({ status: 'ACTIVE' });
      expect(c.canRepay).toBe(true);
      expect(c.canWriteOff).toBe(true);

      c.loan = loan({ status: 'WRITTEN_OFF' });
      expect(c.canUndoWriteOff).toBe(true);
      expect(c.canRepay).toBe(false);
    });

    it('overdue aggregations compute count and outstanding totals', () => {
      c.schedule = [
        installment({ status: 'OVERDUE', totalDue: 900, principalPaid: 100, interestPaid: 50 }),
        installment({ status: 'OVERDUE', totalDue: 500 }),
        installment({ status: 'PAID', totalDue: 900 }),
      ];
      expect(c.overdueCount).toBe(2);
      expect(c.overdueTotal).toBe(900 - 150 + 500); // 1250
    });

    it('sum totals a numeric installment field', () => {
      c.schedule = [installment({ totalDue: 900 }), installment({ totalDue: 100 })];
      expect(c.sum('totalDue')).toBe(1000);
    });

    it('statusVariant / statusLabel map known statuses', () => {
      expect(c.statusVariant('ACTIVE')).toBe('primary');
      expect(c.statusVariant('IN_ARREARS')).toBe('error');
      expect(c.statusVariant('CLOSED_OBLIGATIONS_MET')).toBe('neutral');
      expect(c.statusLabel('IN_ARREARS')).toBe('In Arrears');
    });

    it('installmentVariant maps installment statuses', () => {
      expect(c.installmentVariant('PAID')).toBe('success');
      expect(c.installmentVariant('OVERDUE')).toBe('error');
      expect(c.installmentVariant('PARTIAL')).toBe('warning');
      expect(c.installmentVariant('PENDING')).toBe('neutral');
    });

    it('chargeVariant / chargeLabel reflect the outstanding amount', () => {
      expect(c.chargeLabel(charge({ amount: 50, amountOutstanding: 0 }))).toBe('Paid');
      expect(c.chargeLabel(charge({ amount: 50, amountOutstanding: 20 }))).toBe('Partial');
      expect(c.chargeLabel(charge({ amount: 50, amountOutstanding: 50 }))).toBe('Outstanding');
      expect(c.chargeVariant(charge({ amount: 50, amountOutstanding: 0 }))).toBe('success');
    });
  });

  it('navigates to the new loan after creation', () => {
    TestBed.resetTestingModule();
    configure('new');
    const fixture = TestBed.createComponent(LoanDetailComponent);
    fixture.detectChanges();
    const c = fixture.componentInstance;
    const router = TestBed.inject(Router);
    const navSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    c.newForm = { customerId: 'cust-1', productId: 'p1', principalAmount: 5000, termMonths: 12 };
    c.submitCreate();
    expect(svc.create).toHaveBeenCalledWith(c.newForm);
    expect(navSpy).toHaveBeenCalled();
  });
});
