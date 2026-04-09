import { Injectable, inject } from '@angular/core';
import { Observable, forkJoin, map } from 'rxjs';
import { ApiService } from '../../../core/api/api.service';
import { PageResponse } from '../../../core/models/api-response.model';

export interface DashboardKpi {
  totalCustomers: number;
  activeLoans: number;
  totalDeposits: number;
  todayTransactions: number;
  loansInArrears: number;
  kycPending: number;
}

export interface RecentTransaction {
  id: string;
  accountNumber: string;
  customerName: string;
  transactionType: string;
  amount: number;
  runningBalance: number;
  createdAt: string;
}

export interface KycPendingCustomer {
  id: string;
  fullName: string;
  initials: string;
  kycStatus: string;
  createdAt: string;
}

export interface LoanPortfolioSummary {
  current: number;
  thirtyToSixty: number;
  sixtyToNinety: number;
  ninetyPlus: number;
}

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly api = inject(ApiService);

  getKpis(): Observable<Partial<DashboardKpi>> {
    return forkJoin({
      customers: this.api.getPage<{ kycStatus: string }>('/customers', 0, 1),
      loans:     this.api.getPage<{ status: string }>('/loans', 0, 1),
      accounts:  this.api.getPage<{ balance: number }>('/accounts', 0, 1),
    }).pipe(
      map(({ customers, loans, accounts }) => ({
        totalCustomers:   customers.totalElements,
        activeLoans:      loans.totalElements,
        totalDeposits:    accounts.totalElements,
        todayTransactions: 0,
        loansInArrears:   0,
        kycPending:       0,
      }))
    );
  }

  getRecentTransactions(): Observable<RecentTransaction[]> {
    return this.api.getPage<RecentTransaction>('/transactions', 0, 10)
      .pipe(map(p => p.content));
  }

  getKycPendingCustomers(): Observable<KycPendingCustomer[]> {
    return this.api.getPage<any>('/customers', 0, 5, { kycStatus: 'PENDING_KYC' })
      .pipe(
        map(p => p.content.map((c: any) => ({
          id: c.id,
          fullName: `${c.firstName ?? ''} ${c.lastName ?? ''}`.trim(),
          initials: this.initials(`${c.firstName ?? ''} ${c.lastName ?? ''}`),
          kycStatus: c.kycStatus,
          createdAt: c.createdAt,
        })))
      );
  }

  private initials(name: string): string {
    return name.split(' ').filter(Boolean).slice(0, 2).map(w => w[0].toUpperCase()).join('');
  }
}
