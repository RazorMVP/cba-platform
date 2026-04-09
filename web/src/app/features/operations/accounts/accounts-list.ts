import { Component, inject, OnInit } from '@angular/core';
import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge';
import { AccountService, Account } from './account.service';

@Component({
  selector: 'app-accounts-list',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, StatusBadgeComponent, CurrencyPipe, DatePipe],
  templateUrl: './accounts-list.html',
  styleUrl: './accounts-list.scss',
})
export class AccountsListComponent implements OnInit {
  private readonly svc = inject(AccountService);

  accounts: Account[] = [];
  totalElements = 0;
  page = 0;
  readonly pageSize = 20;
  loading = false;
  typeFilter: Account['accountType'] | '' = '';

  readonly typeFilters: Array<{ label: string; value: Account['accountType'] | '' }> = [
    { label: 'All',           value: '' },
    { label: 'Savings',       value: 'SAVINGS' },
    { label: 'Checking',      value: 'CHECKING' },
    { label: 'Fixed Deposit', value: 'FIXED_DEPOSIT' },
  ];

  ngOnInit(): void { this.loadPage(); }

  loadPage(): void {
    this.loading = true;
    this.svc.list(this.page, this.pageSize)
      .subscribe({ next: p => { this.accounts = p.content; this.totalElements = p.totalElements; this.loading = false; },
                   error: () => { this.loading = false; } });
  }

  prevPage(): void { if (this.page > 0) { this.page--; this.loadPage(); } }
  nextPage(): void { if ((this.page + 1) * this.pageSize < this.totalElements) { this.page++; this.loadPage(); } }

  get totalPages(): number { return Math.ceil(this.totalElements / this.pageSize); }
  get startRow():   number { return this.totalElements === 0 ? 0 : this.page * this.pageSize + 1; }
  get endRow():     number { return Math.min((this.page + 1) * this.pageSize, this.totalElements); }

  statusVariant(s: Account['status']): 'success' | 'warning' | 'error' | 'neutral' {
    return s === 'ACTIVE' ? 'success' : s === 'DORMANT' ? 'warning' : s === 'FROZEN' ? 'error' : 'neutral';
  }
  typeIcon(t: Account['accountType']): string {
    return t === 'SAVINGS' ? 'savings' : t === 'CHECKING' ? 'account_balance_wallet' : 'lock_clock';
  }
}
