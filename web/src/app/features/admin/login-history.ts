import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  AdminService, LoginHistoryEvent, LoginEventSummary, LoginHistoryFilter,
} from './admin.service';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge';

@Component({
  selector: 'app-login-history',
  standalone: true,
  imports: [CommonModule, FormsModule, StatusBadgeComponent],
  templateUrl: './login-history.html',
  styleUrl: './login-history.scss',
})
export class LoginHistoryComponent implements OnInit {
  private readonly svc = inject(AdminService);

  events:    LoginHistoryEvent[] = [];
  summary:   LoginEventSummary | null = null;
  loading    = true;
  error      = '';

  // filters
  filterStatus   = '';
  filterUsername = '';
  filterFrom     = '';
  filterTo       = '';

  // pagination
  page       = 0;
  totalItems = 0;
  pageSize   = 20;

  ngOnInit(): void {
    this.loadSummary();
    this.loadEvents();
  }

  loadSummary(): void {
    this.svc.loginEventSummary(30).subscribe({
      next: s => this.summary = s,
      error: () => {},
    });
  }

  loadEvents(): void {
    this.loading = true;
    const filter: LoginHistoryFilter = {
      page: this.page,
      status:   this.filterStatus   || undefined,
      username: this.filterUsername || undefined,
      from:     this.filterFrom     || undefined,
      to:       this.filterTo       || undefined,
    };
    this.svc.listLoginEvents(filter).subscribe({
      next: r => {
        this.events    = r.content;
        this.totalItems = r.totalElements;
        this.loading   = false;
      },
      error: () => {
        this.error   = 'Failed to load login history.';
        this.loading = false;
      },
    });
  }

  applyFilter(): void {
    this.page = 0;
    this.loadEvents();
  }

  resetFilter(): void {
    this.filterStatus = this.filterUsername = this.filterFrom = this.filterTo = '';
    this.page = 0;
    this.loadEvents();
  }

  prevPage(): void { if (this.page > 0) { this.page--; this.loadEvents(); } }
  nextPage(): void {
    if ((this.page + 1) * this.pageSize < this.totalItems) { this.page++; this.loadEvents(); }
  }

  statusVariant(s: string): 'success' | 'error' | 'warning' | 'neutral' {
    if (s === 'SUCCESS') return 'success';
    if (s === 'FAILURE') return 'error';
    if (s === 'LOCKED')  return 'warning';
    return 'neutral';
  }

  totalPages(): number { return Math.ceil(this.totalItems / this.pageSize); }
}
