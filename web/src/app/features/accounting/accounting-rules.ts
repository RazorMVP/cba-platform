import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  AccountingService, AccountingRule, CreateAccountingRuleRequest, GlAccount,
} from './accounting.service';

@Component({
  selector: 'app-accounting-rules',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './accounting-rules.html',
  styleUrl: './accounting-rules.scss',
})
export class AccountingRulesComponent implements OnInit {
  private readonly svc = inject(AccountingService);

  rules:    AccountingRule[] = [];
  glAccounts: GlAccount[]   = [];
  loading = true;
  error   = '';

  page      = 0;
  pageSize  = 20;
  total     = 0;

  activeModal: 'create' | 'edit' | 'delete' | null = null;
  editTarget:   AccountingRule | null = null;
  deleteTarget: AccountingRule | null = null;
  working    = false;
  modalError = '';

  form: CreateAccountingRuleRequest = this.blank();

  ngOnInit(): void {
    this.svc.listGlAccounts().subscribe({ next: accs => this.glAccounts = accs });
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = '';
    this.svc.listAccountingRules(this.page).subscribe({
      next: res => { this.rules = res.content; this.total = res.totalElements; this.loading = false; },
      error: () => { this.error = 'Failed to load accounting rules.'; this.loading = false; },
    });
  }

  openCreate(): void {
    this.form = this.blank();
    this.editTarget = null;
    this.modalError = '';
    this.working = false;
    this.activeModal = 'create';
  }

  openEdit(r: AccountingRule): void {
    this.form = {
      name: r.name, description: r.description ?? '',
      debitAccountId: r.debitAccountId, creditAccountId: r.creditAccountId,
      allowMultipleDebits: r.allowMultipleDebits, allowMultipleCredits: r.allowMultipleCredits,
      active: r.active,
    };
    this.editTarget = r;
    this.modalError = '';
    this.working = false;
    this.activeModal = 'edit';
  }

  openDelete(r: AccountingRule): void {
    this.deleteTarget = r;
    this.working = false;
    this.activeModal = 'delete';
  }

  closeModal(): void { this.activeModal = null; }

  save(): void {
    this.working = true;
    this.modalError = '';
    const obs = this.activeModal === 'edit' && this.editTarget
      ? this.svc.updateAccountingRule(this.editTarget.id, this.form)
      : this.svc.createAccountingRule(this.form);
    obs.subscribe({
      next: () => { this.closeModal(); this.load(); },
      error: () => { this.modalError = 'Save failed. Please try again.'; this.working = false; },
    });
  }

  confirmDelete(): void {
    if (!this.deleteTarget) return;
    this.working = true;
    this.svc.deleteAccountingRule(this.deleteTarget.id).subscribe({
      next: () => { this.closeModal(); this.load(); },
      error: () => { this.working = false; },
    });
  }

  glLabel(id: string): string {
    const acc = this.glAccounts.find(a => a.id === id);
    return acc ? `${acc.glCode} — ${acc.name}` : id;
  }

  get totalPages(): number { return Math.ceil(this.total / this.pageSize); }
  prev(): void { if (this.page > 0) { this.page--; this.load(); } }
  next(): void { if (this.page < this.totalPages - 1) { this.page++; this.load(); } }

  private blank(): CreateAccountingRuleRequest {
    return { name: '', description: '', debitAccountId: '', creditAccountId: '',
             allowMultipleDebits: false, allowMultipleCredits: false, active: true };
  }
}
