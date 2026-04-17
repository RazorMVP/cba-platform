import { Component, inject, OnInit } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService, AuditLog, AuditFilter } from './admin.service';

const ENTITY_TYPES = [
  'CUSTOMER', 'ACCOUNT', 'LOAN', 'PAYMENT', 'USER', 'ROLE',
  'LOAN_PRODUCT', 'DEPOSIT_PRODUCT', 'TELLER', 'CASHIER', 'SESSION',
  'GL_ACCOUNT', 'JOURNAL_ENTRY', 'GL_CLOSURE', 'CARD', 'DISPUTE',
];

@Component({
  selector: 'app-audit-log',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePipe],
  templateUrl: './audit-log.html',
  styleUrl: './audit-log.scss',
})
export class AuditLogComponent implements OnInit {
  private readonly svc = inject(AdminService);

  readonly entityTypes = ENTITY_TYPES;

  // ── Filter state ─────────────────────────────────────────────────────────
  filter: AuditFilter = {};
  pendingFilter: AuditFilter = {};

  // ── List state ───────────────────────────────────────────────────────────
  rows: AuditLog[]  = [];
  loading           = false;
  error             = '';
  page              = 0;
  pageSize          = 20;
  totalElements     = 0;
  totalPages        = 0;

  // ── Detail panel ─────────────────────────────────────────────────────────
  selected: AuditLog | null = null;

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error   = '';
    this.svc.listAuditLogs(this.page, this.filter).subscribe({
      next: page => {
        this.rows          = page.content;
        this.totalElements = page.totalElements;
        this.totalPages    = page.totalPages;
        this.loading       = false;
      },
      error: () => {
        this.error   = 'Failed to load audit logs.';
        this.loading = false;
      },
    });
  }

  applyFilter(): void {
    this.filter = { ...this.pendingFilter };
    this.page   = 0;
    this.load();
  }

  clearFilter(): void {
    this.pendingFilter = {};
    this.filter        = {};
    this.page          = 0;
    this.load();
  }

  prevPage(): void {
    if (this.page > 0) { this.page--; this.load(); }
  }

  nextPage(): void {
    if (this.page < this.totalPages - 1) { this.page++; this.load(); }
  }

  openDetail(row: AuditLog): void {
    this.selected = row;
  }

  closeDetail(): void {
    this.selected = null;
  }

  // ── Helpers ──────────────────────────────────────────────────────────────
  prettyJson(raw?: string): string {
    if (!raw) return '';
    try { return JSON.stringify(JSON.parse(raw), null, 2); }
    catch { return raw; }
  }

  actionVariant(action: string): string {
    const a = action.toUpperCase();
    if (/^(CREATE|ISSUE|DISBURSE|OPEN)/.test(a)) return 'info';
    if (/^(APPROVE|ACTIVATE|ENABLE|REACTIVATE|AUTHORISE)/.test(a)) return 'success';
    if (/^(REJECT|DELETE|CLOSE|CANCEL|BLOCK|DISABLE|WRITE_OFF|REVOKE)/.test(a)) return 'error';
    if (/^(UPDATE|MODIFY|CHANGE|RESCHEDULE|REAGE)/.test(a)) return 'warning';
    return 'neutral';
  }

  entityVariant(type: string): string {
    const t = type.toUpperCase();
    if (t === 'CUSTOMER') return 'primary';
    if (t === 'LOAN')     return 'warning';
    if (/ACCOUNT/.test(t)) return 'info';
    if (t === 'PAYMENT')  return 'success';
    if (/USER|ROLE/.test(t)) return 'neutral';
    return 'neutral';
  }

  fromIndex(): number { return this.page * this.pageSize + 1; }
  toIndex():   number { return Math.min((this.page + 1) * this.pageSize, this.totalElements); }
}
