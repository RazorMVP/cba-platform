import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
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
  loanId: string;
  chargeName: string;
  chargeTimeType: 'DISBURSEMENT' | 'SPECIFIED_DUE_DATE' | 'INSTALLMENT_FEE' | 'OVERDUE_INSTALLMENT';
  chargeCalculationType: 'FLAT' | 'PERCENT_OF_AMOUNT' | 'PERCENT_OF_AMOUNT_AND_INTEREST';
  amount: number;
  amountPaid: number;
  amountWaived: number;
  amountOutstanding: number;
  dueDate?: string;
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

  writeOff(id: string): Observable<Loan> {
    return this.api.command<Loan>(`/loans/${id}`, 'writeOff');
  }

  recordRepayment(id: string, amount: number, paymentDate: string): Observable<Loan> {
    return this.api.command<Loan>(`/loans/${id}`, 'repayment', { transactionAmount: amount, transactionDate: paymentDate });
  }

  // Charges
  getCharges(id: string): Observable<LoanCharge[]> {
    return this.api.get<LoanCharge[]>(`/loans/${id}/charges`);
  }
  payCharge(loanId: string, chargeId: string): Observable<LoanCharge> {
    return this.api.command<LoanCharge>(`/loans/${loanId}/charges/${chargeId}`, 'pay');
  }
  deleteCharge(loanId: string, chargeId: string): Observable<void> {
    return this.api.delete<void>(`/loans/${loanId}/charges/${chargeId}`);
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
}
