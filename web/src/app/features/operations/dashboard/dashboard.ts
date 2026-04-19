import { Component, inject, OnInit } from '@angular/core';
import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { KpiCardComponent } from '../../../shared/components/kpi-card/kpi-card';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge';
import { DashboardService, DashboardKpi, LoanPortfolioItem, RecentTransaction, KycPendingCustomer, DepositAnalytics, RepaymentAnalytics } from './dashboard.service';

const AVATAR_COLORS = ['#3b82f6','#16a34a','#7c3aed','#ea580c','#db2777','#0891b2'];

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, KpiCardComponent, StatusBadgeComponent, CurrencyPipe, DatePipe],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class DashboardComponent implements OnInit {
  private readonly svc = inject(DashboardService);

  kpis: Partial<DashboardKpi> = {};
  recentTransactions: RecentTransaction[] = [];
  kycPending: KycPendingCustomer[] = [];
  depositAnalytics: DepositAnalytics | null = null;
  repaymentAnalytics: RepaymentAnalytics | null = null;
  loading = true;

  readonly today = new Date();

  loanPortfolio: LoanPortfolioItem[] = [
    { label: 'Current (0–30 days)',   pct: 0, color: '#16a34a' },
    { label: '30–60 days past due',   pct: 0, color: '#ca8a04' },
    { label: '60–90 days past due',   pct: 0, color: '#ea580c' },
    { label: '90+ days / Write-off',  pct: 0, color: '#dc2626' },
  ];

  ngOnInit(): void {
    this.svc.getKpis().subscribe({
      next: kpis => { this.kpis = kpis; this.loading = false; },
      error: () => { this.loading = false; },
    });
    this.svc.getLoanPortfolio().subscribe(p => this.loanPortfolio = p);
    this.svc.getRecentTransactions().subscribe(txns => this.recentTransactions = txns);
    this.svc.getKycPendingCustomers().subscribe(list => this.kycPending = list);
    this.svc.getDepositAnalytics().subscribe(d => this.depositAnalytics = d);
    this.svc.getRepaymentAnalytics().subscribe(r => this.repaymentAnalytics = r);
  }

  avatarColor(index: number): string {
    return AVATAR_COLORS[index % AVATAR_COLORS.length];
  }

  txnAmountClass(amount: number): string {
    return amount >= 0 ? 'amount--credit' : 'amount--debit';
  }

  txnBadgeVariant(type: string): 'success' | 'warning' | 'error' | 'info' {
    const upper = type?.toUpperCase() ?? '';
    if (upper.includes('COMPLETED')) return 'success';
    if (upper.includes('PENDING'))   return 'warning';
    if (upper.includes('FAILED'))    return 'error';
    return 'info';
  }

  get depositBalanceFormatted(): string {
    const bal = (this.kpis as DashboardKpi).depositBalance;
    if (!bal) return '—';
    return this.fmt(Number(bal));
  }

  fmt(v: number): string {
    return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 0 }).format(v);
  }

  collectionBarColor(rate: number): string {
    if (rate >= 90) return '#16a34a';
    if (rate >= 70) return '#ca8a04';
    return '#dc2626';
  }
}
