import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { ApiService } from '../../../core/api/api.service';
import { PageResponse } from '../../../core/models/api-response.model';

export interface Loan {
  id: string;
  loanAccountNumber: string;
  customerId: string;
  customerName?: string;
  productId: string;
  productName?: string;
  principalAmount: number;
  outstandingBalance: number;
  interestRate: number;
  termMonths: number;
  status: 'SUBMITTED' | 'UNDER_REVIEW' | 'APPROVED' | 'DISBURSED' | 'ACTIVE' | 'CLOSED_OBLIGATIONS_MET' | 'WRITTEN_OFF' | 'FORECLOSED' | 'IN_ARREARS' | 'REJECTED';
  disbursedDate?: string;
  closedDate?: string;
  createdAt: string;
  /** Days since the oldest unpaid instalment fell due. */
  daysPastDue?: number;
  /** 90+ days past due: interest and penalties held in suspense (CBN). */
  nonPerforming?: boolean;
  interestInSuspense?: number;
  /** Origination fee not yet released to interest income. */
  deferredFeeRemaining?: number;
  /** IFRS 9 stage at the last measurement: 1, 2 or 3 (null before the first). */
  eclStage?: number | null;
  /** Expected credit losses held against the loan (loan loss allowance). */
  lossAllowance?: number;
  /** CBN prudential provision at the last measurement. */
  prudentialProvision?: number;
  writtenOffOn?: string | null;
  writeOffReason?: string | null;
  /** Board (or delegated authority) approval for the write-off. */
  writeOffApprovalReference?: string | null;
  /** Gross carrying amount derecognised at write-off. */
  writtenOffAmount?: number | null;
  /** Cash collected since the write-off. */
  recoveredAmount?: number;
  /** Interest waived: accrued interest derecognised plus future interest given up. */
  interestWaived?: number | null;
  /** IFRS 9 §5.4.3 modification discount not yet unwound into interest income. */
  modificationDiscountRemaining?: number | null;
}

export interface RepaymentInstallment {
  id: string;
  loanId: string;
  dueDate: string;
  principalDue: number;
  interestDue: number;
  totalDue: number;
  principalPaid: number;
  interestPaid: number;
  feesPaid?: number;
  totalPaid?: number;
  /** Accrued interest waived (still inside interestDue; totalDue is net of it). */
  interestWaived?: number;
  /** Future interest waived (taken out of interestDue). */
  interestCancelled?: number;
  status: 'PENDING' | 'PAID' | 'PARTIALLY_PAID' | 'OVERDUE' | 'WAIVED';
}

/** Today as YYYY-MM-DD in local time (the schedule's due dates are local business dates). */
function todayIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** Fallen due and not settled (settled = paid, or the rest waived). The API doesn't set OVERDUE itself. */
export function isOverdue(i: RepaymentInstallment): boolean {
  if (i.status === 'PAID' || i.status === 'WAIVED') return false;
  return i.status === 'OVERDUE' || (!!i.dueDate && i.dueDate < todayIso());
}

/** What is still owed on an instalment: its total due (net of waived interest) less what was paid. */
export function stillOwed(i: RepaymentInstallment): number {
  const paid = i.totalPaid ?? (i.principalPaid ?? 0) + (i.interestPaid ?? 0) + (i.feesPaid ?? 0);
  return Math.max(0, i.totalDue - paid);
}

/** ACCRUED: only interest accrued and unpaid. ALL: that and every future instalment's interest. */
export type WaiverScope = 'ACCRUED' | 'ALL';

export interface WaiveInterestRequest {
  reason: string;
  scope: WaiverScope;
  waiveDate?: string;
  approvalReference?: string;
}

export interface LoanCreateRequest {
  customerId: string;
  productId: string;
  principalAmount: number;
  termMonths: number;
  interestRate?: number;
  disbursementDate?: string;
}

export interface LoanCharge {
  id: string;
  name: string;
  chargeTimeType: string;
  chargeCalculation: string;
  currencyCode: string;
  amount: number;
  amountPaid: number;
  amountWaived: number;
  amountOutstanding: number;
  paid: boolean;
  waived: boolean;
  dueForCollectionAsOfDate?: string;
  /** When the charge became income; null for a fee not yet due (cannot be paid yet). */
  incomeRecognizedOn?: string | null;
}

/**
 * Where a loan payment comes from: the borrower's linked account (ACCOUNT, default; or
 * another of their accounts via sourceAccountId) or cash at an open teller session (CASH).
 */
export interface LoanPayment {
  paymentMethod: 'ACCOUNT' | 'CASH';
  sourceAccountId?: string;
  tellerSessionId?: string;
}

/** POST /loans/{id}/repayments response: how the payment was split. */
export interface LoanRepaymentResult {
  loanId: string;
  loanAccountNumber: string;
  amountPaid: number;
  principalPortion: number;
  interestPortion: number;
  feePortion: number;
  outstandingBalanceAfter: number;
  paymentDate: string;
  paymentMethod: string;
  referenceNumber?: string;
}

/** What settling the loan on `date` costs; interest not yet due is not charged. */
export interface ForeclosureQuote {
  loanId: string;
  loanAccountNumber: string;
  date: string;
  currencyCode: string;
  principal: number;
  interest: number;
  fees: number;
  charges: number;
  total: number;
}

export interface AvailableCharge {
  id: string;
  name: string;
  amount: number;
  chargeCalculation: string;
  chargeTimeType: string;
}

export interface Guarantor {
  id: string;
  guarantorType: 'EXISTING_CUSTOMER' | 'EXTERNAL';
  customerId?: string;
  customerName?: string;
  firstname?: string;
  lastname?: string;
  email?: string;
  phone?: string;
}

export interface Collateral {
  id: string;
  collateralType: string;
  value: number;
  description?: string;
  currencyCode: string;
}

export interface AuditEntry {
  id: string;
  entityType: string;
  entityId: string;
  action: string;
  changedBy: string;
  changedAt: string;
  oldValues?: Record<string, unknown>;
  newValues?: Record<string, unknown>;
}

// ── Notes ─────────────────────────────────────────────────────────────────────
export interface LoanNote {
  id: string;
  note: string;
  createdBy: string;
  createdAt: string;
}

// ── Documents ─────────────────────────────────────────────────────────────────
export interface LoanDocument {
  id: string;
  fileName: string;
  fileSize: number;
  contentType: string;
  description?: string;
  createdAt: string;
}

// ── Reschedule ────────────────────────────────────────────────────────────────
export type RescheduleStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface LoanRescheduleRequest {
  id: string;
  loanId: string;
  rescheduleReason: string;
  newInterestRate: number | null;
  graceOnPrincipal: number | null;
  graceOnInterest: number | null;
  extraTerms: number | null;
  recalculateInterest: boolean;
  status: RescheduleStatus;
  submittedOnDate: string;
  approvedOnDate: string | null;
  rejectedOnDate: string | null;
}

export interface CreateRescheduleRequest {
  loanId: string;
  rescheduleReason: string;
  newInterestRate?: number;
  graceOnPrincipal?: number;
  graceOnInterest?: number;
  extraTerms?: number;
  recalculateInterest: boolean;
}

// ── Re-aging / Re-amortization ────────────────────────────────────────────────
export type FrequencyType = 'DAYS' | 'WEEKS' | 'MONTHS';

export interface ReagingRequest {
  id: string;
  loanId: string;
  frequencyType: FrequencyType;
  frequency: number;
  startDate: string;
  isPreview: boolean;
}

export interface CreateReagingRequest {
  frequencyType: FrequencyType;
  frequency: number;
  startDate: string;
  isPreview: boolean;
}

@Injectable({ providedIn: 'root' })
export class LoanService {
  private readonly api = inject(ApiService);

  list(page = 0, size = 20, status?: string, customerId?: string): Observable<PageResponse<Loan>> {
    const params: Record<string, string> = {};
    if (status)     params['status']     = status;
    if (customerId) params['customerId'] = customerId;
    return this.api.getPage<Loan>('/loans', page, size, params);
  }

  get(id: string): Observable<Loan> {
    return this.api.get<Loan>(`/loans/${id}`);
  }

  getSchedule(id: string): Observable<RepaymentInstallment[]> {
    return this.api.get<RepaymentInstallment[]>(`/loans/${id}/repayment-schedule`);
  }

  create(body: LoanCreateRequest): Observable<Loan> {
    return this.api.post<Loan>('/loans', body);
  }

  /** Approves the full requested principal (the API has no partial approval). */
  approve(id: string): Observable<Loan> {
    return this.api.put<Loan>(`/loans/${id}/approve`, {});
  }

  disburse(id: string): Observable<Loan> {
    return this.api.put<Loan>(`/loans/${id}/disburse`, {});
  }

  reject(id: string, reason: string): Observable<Loan> {
    return this.api.put<Loan>(`/loans/${id}/reject`, { reason });
  }

  /** Writes a credit-impaired loan off; {@code approvalReference} is the board approval (required). */
  writeOff(id: string, reason: string, approvalReference: string, writeOffDate?: string): Observable<Loan> {
    return this.api.post<Loan>(`/loans/${id}/write-off`, { reason, approvalReference, writeOffDate });
  }

  undoWriteOff(id: string): Observable<Loan> {
    return this.api.post<Loan>(`/loans/${id}/undo-write-off`, {});
  }

  /**
   * Waives interest: accrued interest is derecognised (a loss, or out of suspense); with
   * scope ALL future interest leaves the schedule as a modification loss that unwinds.
   */
  waiveInterest(id: string, request: WaiveInterestRequest): Observable<Loan> {
    return this.api.post<Loan>(`/loans/${id}/waive-interest`, request);
  }

  /** Settles the loan for its foreclosure quote, paid from the given source. */
  foreclose(id: string, reason: string, foreclosureDate?: string,
            payment: LoanPayment = { paymentMethod: 'ACCOUNT' }): Observable<Loan> {
    return this.api.post<Loan>(`/loans/${id}/foreclose`, { reason, foreclosureDate, ...payment });
  }

  getForeclosureQuote(id: string, date?: string): Observable<ForeclosureQuote> {
    return this.api.get<ForeclosureQuote>(`/loans/${id}/foreclosure-quote`, date ? { date } : undefined);
  }

  recordRepayment(id: string, amount: number, paymentDate: string,
                  payment: LoanPayment = { paymentMethod: 'ACCOUNT' }): Observable<LoanRepaymentResult> {
    return this.api.post<LoanRepaymentResult>(`/loans/${id}/repayments`, { amount, paymentDate, ...payment });
  }

  // Charges
  getCharges(id: string): Observable<LoanCharge[]> {
    return this.api.getPage<LoanCharge>(`/loans/${id}/charges`, 0, 100)
      .pipe(map(p => p.content));
  }
  addCharge(loanId: string, chargeDefinitionId: string, amount: number, dueDate?: string): Observable<LoanCharge> {
    return this.api.post<LoanCharge>(`/loans/${loanId}/charges`,
      { chargeDefinitionId, amount, dueDate: dueDate || null });
  }
  payCharge(loanId: string, chargeId: string,
            payment: LoanPayment = { paymentMethod: 'ACCOUNT' }): Observable<LoanCharge> {
    return this.api.post<LoanCharge>(`/loans/${loanId}/charges/${chargeId}/pay`, payment);
  }
  waiveCharge(loanId: string, chargeId: string): Observable<LoanCharge> {
    return this.api.post<LoanCharge>(`/loans/${loanId}/charges/${chargeId}/waive`, {});
  }
  deleteCharge(loanId: string, chargeId: string): Observable<void> {
    return this.api.delete<void>(`/loans/${loanId}/charges/${chargeId}`);
  }
  listAvailableCharges(): Observable<AvailableCharge[]> {
    return this.api.getPage<AvailableCharge>('/charges', 0, 100, { appliesTo: 'LOAN' })
      .pipe(map(p => p.content));
  }

  // Guarantors
  getGuarantors(id: string): Observable<Guarantor[]> {
    return this.api.get<Guarantor[]>(`/loans/${id}/guarantors`);
  }

  // Collateral
  getCollateral(id: string): Observable<Collateral[]> {
    return this.api.get<Collateral[]>(`/loans/${id}/collaterals`);
  }

  // Notes
  getNotes(id: string): Observable<LoanNote[]> {
    return this.api.get<LoanNote[]>(`/loans/${id}/notes`);
  }
  addNote(id: string, note: string): Observable<LoanNote> {
    return this.api.post<LoanNote>(`/loans/${id}/notes`, { note });
  }
  deleteNote(loanId: string, noteId: string): Observable<void> {
    return this.api.delete<void>(`/loans/${loanId}/notes/${noteId}`);
  }

  // Documents
  getDocuments(id: string): Observable<LoanDocument[]> {
    return this.api.get<LoanDocument[]>(`/loans/${id}/documents`);
  }

  // Audit log for this loan
  getAuditLog(id: string): Observable<AuditEntry[]> {
    return this.api.get<AuditEntry[]>(`/audits`, { entityType: 'LOAN', entityId: id });
  }

  // Reschedule
  listRescheduleRequests(loanId: string): Observable<LoanRescheduleRequest[]> {
    return this.api.get<LoanRescheduleRequest[]>('/loanreschedule', { loanId });
  }

  createRescheduleRequest(req: CreateRescheduleRequest): Observable<LoanRescheduleRequest> {
    return this.api.post<LoanRescheduleRequest>('/loanreschedule', req);
  }

  approveReschedule(id: string): Observable<LoanRescheduleRequest> {
    return this.api.command<LoanRescheduleRequest>(`/loanreschedule/${id}`, 'approve');
  }

  rejectReschedule(id: string): Observable<LoanRescheduleRequest> {
    return this.api.command<LoanRescheduleRequest>(`/loanreschedule/${id}`, 'reject');
  }

  // Re-aging
  listReaging(loanId: string): Observable<ReagingRequest[]> {
    return this.api.get<ReagingRequest[]>(`/loans/${loanId}/reaging`);
  }

  createReaging(loanId: string, req: CreateReagingRequest): Observable<ReagingRequest> {
    return this.api.post<ReagingRequest>(`/loans/${loanId}/reaging`, req);
  }

  triggerReamortization(loanId: string): Observable<void> {
    return this.api.post<void>(`/loans/${loanId}/reamortization`, {});
  }
}
