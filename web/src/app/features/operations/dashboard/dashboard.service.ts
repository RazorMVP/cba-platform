import { Injectable, inject } from '@angular/core';
import { Observable, forkJoin, map, catchError, of } from 'rxjs';
import { ApiService } from '../../../core/api/api.service';

export interface DashboardKpi {
  totalCustomers: number;
  activeLoans: number;
  totalDeposits: number;
  depositBalance: number;
  todayTransactions: number;
  loansInArrears: number;
  kycPending: number;
}

export interface LoanPortfolioItem {
  label: string;
  pct: number;
  color: string;
  count?: number;
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

export interface DepositAnalytics {
  savingsCount: number;
  savingsBalance: number;
  checkingCount: number;
  checkingBalance: number;
  fixedDepositCount: number;
  fixedDepositBalance: number;
  newThisMonth: number;
  averageBalance: number;
}

export interface RepaymentAnalytics {
  installmentsDueThisMonth: number;
  installmentsPaidThisMonth: number;
  amountDueThisMonth: number;
  amountCollectedThisMonth: number;
  collectionRate: number;
  overdueInstallmentCount: number;
  overdueBalance: number;
}

export interface KycPendingCustomer {
  id: string;
  fullName: string;
  initials: string;
  kycStatus: string;
  createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly api = inject(ApiService);

  getKpis(): Observable<DashboardKpi> {
    return this.api.get<DashboardKpi>('/dashboard').pipe(
      catchError(() =>
        // Fallback to individual queries if endpoint not available
        forkJoin({
          customers: this.api.getPage<{ kycStatus: string }>('/customers', 0, 1),
          loans:     this.api.getPage<{ status: string }>('/loans', 0, 1),
          accounts:  this.api.getPage<{ balance: number }>('/accounts', 0, 1),
        }).pipe(
          map(({ customers, loans, accounts }) => ({
            totalCustomers:    customers.totalElements,
            activeLoans:       loans.totalElements,
            totalDeposits:     accounts.totalElements,
            depositBalance:    0,
            todayTransactions: 0,
            loansInArrears:    0,
            kycPending:        0,
          }))
        )
      )
    );
  }

  getLoanPortfolio(): Observable<LoanPortfolioItem[]> {
    return this.api.get<{
      pctCurrent: number; pct30to60: number; pct60to90: number; pct90plus: number;
      countActive: number; countInArrears: number; countWrittenOff: number;
    }>('/dashboard/analytics/loans').pipe(
      map(d => [
        { label: 'Current (0–30 days)',  pct: d.pctCurrent, color: '#16a34a', count: d.countActive },
        { label: '30–60 days past due',  pct: d.pct30to60,  color: '#ca8a04' },
        { label: '60–90 days past due',  pct: d.pct60to90,  color: '#ea580c', count: d.countInArrears },
        { label: '90+ days / Write-off', pct: d.pct90plus,  color: '#dc2626', count: d.countWrittenOff },
      ]),
      catchError(() => of([
        { label: 'Current (0–30 days)',  pct: 0, color: '#16a34a' },
        { label: '30–60 days past due',  pct: 0, color: '#ca8a04' },
        { label: '60–90 days past due',  pct: 0, color: '#ea580c' },
        { label: '90+ days / Write-off', pct: 0, color: '#dc2626' },
      ]))
    );
  }

  getDepositAnalytics(): Observable<DepositAnalytics> {
    return this.api.get<DepositAnalytics>('/dashboard/analytics/deposits').pipe(
      catchError(() => of({
        savingsCount: 0, savingsBalance: 0,
        checkingCount: 0, checkingBalance: 0,
        fixedDepositCount: 0, fixedDepositBalance: 0,
        newThisMonth: 0, averageBalance: 0,
      }))
    );
  }

  getRepaymentAnalytics(): Observable<RepaymentAnalytics> {
    return this.api.get<RepaymentAnalytics>('/dashboard/analytics/repayments').pipe(
      catchError(() => of({
        installmentsDueThisMonth: 0, installmentsPaidThisMonth: 0,
        amountDueThisMonth: 0, amountCollectedThisMonth: 0,
        collectionRate: 0, overdueInstallmentCount: 0, overdueBalance: 0,
      }))
    );
  }

  getRecentTransactions(): Observable<RecentTransaction[]> {
    return this.api.getPage<RecentTransaction>('/transactions', 0, 10)
      .pipe(map(p => p.content), catchError(() => of([])));
  }

  getKycPendingCustomers(): Observable<KycPendingCustomer[]> {
    return this.api.getPage<{ id: string; firstName?: string; lastName?: string; kycStatus: string; createdAt: string }>(
      '/customers', 0, 5, { kycStatus: 'PENDING_KYC' }
    ).pipe(
        map(p => p.content.map(c => ({
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
