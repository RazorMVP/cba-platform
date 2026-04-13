import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AccountingService, FinancialActivityAccount, FinancialActivityRequest } from './accounting.service';

@Component({
  selector: 'app-financial-activity-accounts',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './financial-activity-accounts.html',
  styleUrl: './financial-activity-accounts.scss',
})
export class FinancialActivityAccountsComponent implements OnInit {
  private readonly svc = inject(AccountingService);

  items:   FinancialActivityAccount[] = [];
  loading  = true;
  error    = '';

  // Modal state
  showModal    = false;
  editingId: string | null = null;
  working      = false;
  modalError   = '';

  form: FinancialActivityRequest = { financialActivity: 'ASSET_FUND_SOURCE', glAccountId: '' };

  // GL accounts for picker (DETAIL type only)
  glAccounts: { id: string; glCode: string; name: string }[] = [];

  readonly activityLabels: Record<string, string> = {
    ASSET_FUND_SOURCE:              'Asset — Fund Source',
    ASSET_LOAN_PORTFOLIO:           'Asset — Loan Portfolio',
    ASSET_RECEIVABLE:               'Asset — Receivable',
    ASSET_OVERPAYMENT_LIABILITY:    'Asset — Overpayment / Liability',
    LIABILITY_LINKED_TO_FLOAT:      'Liability — Linked to Float',
    LIABILITY_PAYMENT_GATEWAY:      'Liability — Payment Gateway',
    LIABILITY_TRANSFER_IN_SUSPENSE: 'Liability — Transfer in Suspense',
    INCOME_INTEREST:                'Income — Interest',
    INCOME_FEE:                     'Income — Fee',
    EXPENSE_DEPRECIATION:           'Expense — Depreciation',
    EXPENSE_LOAN_LOSSES:            'Expense — Loan Losses',
  };

  ngOnInit(): void {
    this.load();
    this.svc.listGlAccounts({ usage: 'DETAIL' }).subscribe({
      next: list => { this.glAccounts = list.map(g => ({ id: g.id, glCode: g.glCode, name: g.name })); },
    });
  }

  load(): void {
    this.loading = true;
    this.svc.listFinancialActivityAccounts().subscribe({
      next:  items => { this.items = items; this.loading = false; },
      error: ()    => { this.error = 'Failed to load financial activity accounts.'; this.loading = false; },
    });
  }

  openCreate(): void {
    this.editingId  = null;
    this.form       = { financialActivity: 'ASSET_FUND_SOURCE', glAccountId: '' };
    this.modalError = '';
    this.showModal  = true;
  }

  openEdit(item: FinancialActivityAccount): void {
    this.editingId  = item.id;
    this.form       = { financialActivity: item.financialActivity, glAccountId: item.glAccountId };
    this.modalError = '';
    this.showModal  = true;
  }

  closeModal(): void { if (!this.working) this.showModal = false; }

  save(): void {
    this.working    = true;
    this.modalError = '';
    const req$ = this.editingId
      ? this.svc.updateFinancialActivityAccount(this.editingId, this.form)
      : this.svc.createFinancialActivityAccount(this.form);

    req$.subscribe({
      next: () => {
        this.working   = false;
        this.showModal = false;
        this.load();
      },
      error: () => {
        this.modalError = 'Save failed. Check that this financial activity is not already mapped.';
        this.working    = false;
      },
    });
  }

  remove(id: string): void {
    if (!confirm('Remove this GL mapping?')) return;
    this.svc.deleteFinancialActivityAccount(id).subscribe({ next: () => this.load() });
  }

  activityLabel(activity: string): string {
    return this.activityLabels[activity] ?? activity;
  }

  glAccountLabel(id: string): string {
    const g = this.glAccounts.find(a => a.id === id);
    return g ? `${g.glCode} — ${g.name}` : id;
  }

  activities = Object.keys(this.activityLabels);
}
