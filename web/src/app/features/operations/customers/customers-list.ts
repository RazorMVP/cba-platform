import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { debounceTime, distinctUntilChanged, Subject, switchMap } from 'rxjs';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge';
import { CustomerService, Customer, KycStatus } from './customer.service';

const AVATAR_COLORS = ['#3b82f6','#16a34a','#7c3aed','#ea580c','#db2777','#0891b2','#ca8a04'];

@Component({
  selector: 'app-customers-list',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, PageHeaderComponent, StatusBadgeComponent],
  templateUrl: './customers-list.html',
  styleUrl: './customers-list.scss',
})
export class CustomersListComponent implements OnInit {
  private readonly svc = inject(CustomerService);
  private readonly search$ = new Subject<string>();

  customers: Customer[] = [];
  totalElements = 0;
  page = 0;
  readonly pageSize = 20;
  loading = false;

  searchQuery = '';
  activeFilter: KycStatus | '' = '';

  readonly kycFilters: Array<{ label: string; value: KycStatus | '' }> = [
    { label: 'All',         value: '' },
    { label: 'Active',      value: 'ACTIVE' },
    { label: 'Pending KYC', value: 'PENDING_KYC' },
    { label: 'Suspended',   value: 'SUSPENDED' },
    { label: 'Closed',      value: 'CLOSED' },
  ];

  ngOnInit(): void {
    this.loadPage();

    this.search$
      .pipe(debounceTime(300), distinctUntilChanged(),
            switchMap(q => { this.loading = true; return this.svc.list(0, this.pageSize, q, this.activeFilter || undefined); }))
      .subscribe(p => { this.customers = p.content; this.totalElements = p.totalElements; this.page = 0; this.loading = false; });
  }

  loadPage(): void {
    this.loading = true;
    this.svc.list(this.page, this.pageSize, this.searchQuery || undefined, this.activeFilter || undefined)
      .subscribe({ next: p => { this.customers = p.content; this.totalElements = p.totalElements; this.loading = false; },
                   error: () => { this.loading = false; } });
  }

  onSearch(q: string): void { this.search$.next(q); }
  onFilter(f: KycStatus | ''): void { this.activeFilter = f; this.page = 0; this.loadPage(); }
  prevPage(): void { if (this.page > 0) { this.page--; this.loadPage(); } }
  nextPage(): void { if ((this.page + 1) * this.pageSize < this.totalElements) { this.page++; this.loadPage(); } }

  get totalPages(): number { return Math.ceil(this.totalElements / this.pageSize); }
  get startRow():   number { return this.totalElements === 0 ? 0 : this.page * this.pageSize + 1; }
  get endRow():     number { return Math.min((this.page + 1) * this.pageSize, this.totalElements); }

  avatarColor(index: number): string { return AVATAR_COLORS[index % AVATAR_COLORS.length]; }
  initials(c: Customer): string {
    return `${c.firstName?.[0] ?? ''}${c.lastName?.[0] ?? ''}`.toUpperCase();
  }
  kycVariant(status: KycStatus): 'success' | 'warning' | 'error' | 'neutral' {
    return status === 'ACTIVE'      ? 'success'
         : status === 'PENDING_KYC' ? 'warning'
         : status === 'SUSPENDED'   ? 'error'
         : 'neutral';
  }
  kycLabel(status: KycStatus): string {
    return status === 'PENDING_KYC' ? 'Pending KYC' : status.charAt(0) + status.slice(1).toLowerCase();
  }
}
