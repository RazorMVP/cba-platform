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

@Injectable({ providedIn: 'root' })
export class LoanService {
  private readonly api = inject(ApiService);

  list(page = 0, size = 20, status?: string): Observable<PageResponse<Loan>> {
    const params: Record<string, string> = {};
    if (status) params['status'] = status;
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
}
