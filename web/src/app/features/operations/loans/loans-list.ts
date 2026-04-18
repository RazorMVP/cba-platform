import { Component, inject, OnInit } from '@angular/core';
import { CommonModule, CurrencyPipe, DatePipe, PercentPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge';
import { LoanService, Loan, RepaymentInstallment } from './loan.service';

@Component({
  selector: 'app-loans-list',
  standalone: true,
  imports: [CommonModule, RouterLink, StatusBadgeComponent, CurrencyPipe, DatePipe, PercentPipe],
  templateUrl: './loans-list.html',
  styleUrl: './loans-list.scss',
})
export class LoansListComponent implements OnInit {
  private readonly svc = inject(LoanService);

  loans: Loan[] = [];
  totalElements = 0;
  page = 0;
  readonly pageSize = 20;
  loading = false;
  statusFilter = '';
  selectedLoan: Loan | null = null;
  schedule: RepaymentInstallment[] = [];
  scheduleLoading = false;

  readonly pipeline: Array<{ label: string; status: string; color: string }> = [
    { label: 'Submitted',    status: 'SUBMITTED',    color: '#2563eb' },
    { label: 'Under Review', status: 'UNDER_REVIEW',  color: '#ca8a04' },
    { label: 'Approved',     status: 'APPROVED',      color: '#16a34a' },
    { label: 'Active',       status: 'ACTIVE',        color: '#7c3aed' },
    { label: 'In Arrears',   status: 'IN_ARREARS',    color: '#dc2626' },
    { label: 'Closed',       status: 'CLOSED_OBLIGATIONS_MET', color: '#888' },
  ];

  readonly statusFilters = ['', 'SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'ACTIVE', 'IN_ARREARS', 'CLOSED_OBLIGATIONS_MET'];

  ngOnInit(): void { this.loadPage(); }

  loadPage(): void {
    this.loading = true;
    this.svc.list(this.page, this.pageSize, this.statusFilter || undefined)
      .subscribe({ next: p => { this.loans = p.content; this.totalElements = p.totalElements; this.loading = false; },
                   error: () => { this.loading = false; } });
  }

  selectLoan(loan: Loan): void {
    this.selectedLoan = loan;
    this.schedule = [];
    this.scheduleLoading = true;
    this.svc.getSchedule(loan.id)
      .subscribe({ next: s => { this.schedule = s; this.scheduleLoading = false; },
                   error: () => { this.scheduleLoading = false; } });
  }

  prevPage(): void { if (this.page > 0) { this.page--; this.loadPage(); } }
  nextPage(): void { if ((this.page + 1) * this.pageSize < this.totalElements) { this.page++; this.loadPage(); } }

  get totalPages(): number { return Math.ceil(this.totalElements / this.pageSize); }
  get startRow():   number { return this.totalElements === 0 ? 0 : this.page * this.pageSize + 1; }
  get endRow():     number { return Math.min((this.page + 1) * this.pageSize, this.totalElements); }

  repaidPct(loan: Loan): number {
    if (!loan.principalAmount) return 0;
    return Math.round((1 - loan.outstandingBalance / loan.principalAmount) * 100);
  }

  statusVariant(s: Loan['status']): 'success' | 'warning' | 'error' | 'info' | 'neutral' | 'primary' {
    const map: Record<string, 'success' | 'warning' | 'error' | 'info' | 'neutral' | 'primary'> = {
      ACTIVE: 'primary', SUBMITTED: 'info', UNDER_REVIEW: 'warning',
      APPROVED: 'success', DISBURSED: 'success',
      IN_ARREARS: 'error', WRITTEN_OFF: 'error', FORECLOSED: 'error', REJECTED: 'error',
      CLOSED_OBLIGATIONS_MET: 'neutral',
    };
    return map[s] ?? 'neutral';
  }

  statusLabel(s: Loan['status']): string {
    const map: Record<string, string> = {
      SUBMITTED: 'Submitted', UNDER_REVIEW: 'Under Review', APPROVED: 'Approved',
      DISBURSED: 'Disbursed', ACTIVE: 'Active', IN_ARREARS: 'In Arrears',
      CLOSED_OBLIGATIONS_MET: 'Closed', WRITTEN_OFF: 'Written Off',
      FORECLOSED: 'Foreclosed', REJECTED: 'Rejected',
    };
    return map[s] ?? s;
  }

  get overdueCount(): number {
    return this.schedule.filter(i => i.status === 'OVERDUE').length;
  }

  get overdueTotal(): number {
    return this.schedule
      .filter(i => i.status === 'OVERDUE')
      .reduce((sum, i) => sum + (i.totalDue - (i.principalPaid ?? 0) - (i.interestPaid ?? 0)), 0);
  }
}
