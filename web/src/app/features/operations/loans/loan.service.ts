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
  status: 'SUBMITTED' | 'UNDER_REVIEW' | 'APPROVED' | 'DISBURSED' | 'ACTIVE' | 'CLOSED_OBLIGATIONS_MET' | 'WRITTEN_OFF' | 'IN_ARREARS';
  disbursedDate?: string;
  closedDate?: string;
  createdAt: string;
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
  status: 'PENDING' | 'PAID' | 'PARTIAL' | 'OVERDUE';
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

  approve(id: string, approvedAmount?: number): Observable<Loan> {
    return this.api.command<Loan>(`/loans/${id}`, 'approve', { approvedAmount });
  }

  disburse(id: string): Observable<Loan> {
    return this.api.command<Loan>(`/loans/${id}`, 'disburse');
  }

  reject(id: string, reason: string): Observable<Loan> {
    return this.api.command<Loan>(`/loans/${id}`, 'reject', { reason });
  }

  writeOff(id: string, reason: string, writeOffDate?: string): Observable<Loan> {
    return this.api.post<Loan>(`/loans/${id}/write-off`, { reason, writeOffDate });
  }

  recordRepayment(id: string, amount: number, paymentDate: string): Observable<Loan> {
    return this.api.command<Loan>(`/loans/${id}`, 'repayment', { transactionAmount: amount, transactionDate: paymentDate });
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
  payCharge(loanId: string, chargeId: string): Observable<LoanCharge> {
    return this.api.post<LoanCharge>(`/loans/${loanId}/charges/${chargeId}/pay`, {});
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
