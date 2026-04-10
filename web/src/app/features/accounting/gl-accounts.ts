import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge';
import {
  AccountingService, GlAccount, GlAccountRequest,
  GlAccountType, GlAccountUsage,
} from './accounting.service';

type TypeFilter = '' | GlAccountType;
type ModalType  = 'create' | 'edit' | null;

const ACCOUNT_TYPES: GlAccountType[] = ['ASSET', 'LIABILITY', 'EQUITY', 'INCOME', 'EXPENSE'];

@Component({
  selector: 'app-gl-accounts',
  standalone: true,
  imports: [CommonModule, FormsModule, StatusBadgeComponent],
  templateUrl: './gl-accounts.html',
  styleUrl: './gl-accounts.scss',
})
export class GlAccountsComponent implements OnInit {
  private readonly svc = inject(AccountingService);

  accounts: GlAccount[] = [];
  filtered: GlAccount[] = [];
  loading = true;
  error   = '';

  searchQuery  = '';
  typeFilter: TypeFilter = '';
  showDisabled = false;

  readonly accountTypes = ACCOUNT_TYPES;
  readonly usages: GlAccountUsage[] = ['HEADER', 'DETAIL'];

  // ── Modal ──────────────────────────────────────────────────────────────────
  activeModal:   ModalType = null;
  editingId      = '';
  modalWorking   = false;
  modalError     = '';

  form: GlAccountRequest = this.blankForm();

  // ── Parent accounts for dropdown ───────────────────────────────────────────
  get headerAccounts(): GlAccount[] {
    return this.accounts.filter(a => a.usage === 'HEADER' && !a.disabled);
  }

  ngOnInit(): void {
    this.loadAccounts();
  }

  loadAccounts(): void {
    this.svc.listGlAccounts().subscribe({
      next: list => {
        this.accounts = list;
        this.applyFilter();
        this.loading = false;
      },
      error: () => { this.error = 'Failed to load GL accounts.'; this.loading = false; },
    });
  }

  applyFilter(): void {
    let result = this.accounts;
    if (!this.showDisabled) result = result.filter(a => !a.disabled);
    if (this.typeFilter)    result = result.filter(a => a.accountType === this.typeFilter);
    if (this.searchQuery) {
      const q = this.searchQuery.toLowerCase();
      result = result.filter(a =>
        a.glCode.toLowerCase().includes(q) || a.name.toLowerCase().includes(q));
    }
    this.filtered = result;
  }

  countByType(t: GlAccountType): number {
    return this.accounts.filter(a => a.accountType === t && !a.disabled).length;
  }

  // ── Lifecycle actions ──────────────────────────────────────────────────────

  toggleDisable(a: GlAccount): void {
    const call = a.disabled ? this.svc.enableGlAccount(a.id) : this.svc.disableGlAccount(a.id);
    call.subscribe({
      next: updated => {
        this.accounts = this.accounts.map(x => x.id === updated.id ? updated : x);
        this.applyFilter();
      },
      error: () => {},
    });
  }

  // ── Modals ─────────────────────────────────────────────────────────────────

  openCreateModal(): void {
    this.activeModal  = 'create';
    this.editingId    = '';
    this.form         = this.blankForm();
    this.modalWorking = false;
    this.modalError   = '';
  }

  openEditModal(a: GlAccount): void {
    this.activeModal  = 'edit';
    this.editingId    = a.id;
    this.form = {
      glCode:               a.glCode,
      name:                 a.name,
      accountType:          a.accountType,
      usage:                a.usage,
      manualEntriesAllowed: a.manualEntriesAllowed,
      description:          a.description ?? '',
      parentId:             a.parentId ?? '',
    };
    this.modalWorking = false;
    this.modalError   = '';
  }

  submitModal(): void {
    if (!this.form.glCode || !this.form.name) return;
    this.modalWorking = true;
    const req: GlAccountRequest = {
      ...this.form,
      parentId:    this.form.parentId    || undefined,
      description: this.form.description || undefined,
    };
    const call = this.activeModal === 'create'
      ? this.svc.createGlAccount(req)
      : this.svc.updateGlAccount(this.editingId, req);

    call.subscribe({
      next: a => {
        if (this.activeModal === 'create') {
          this.accounts = [...this.accounts, a];
        } else {
          this.accounts = this.accounts.map(x => x.id === a.id ? a : x);
        }
        this.applyFilter();
        this.modalWorking = false;
        this.activeModal  = null;
      },
      error: () => { this.modalError = 'Failed to save GL account.'; this.modalWorking = false; },
    });
  }

  closeModal(): void { if (!this.modalWorking) this.activeModal = null; }

  // ── Display helpers ────────────────────────────────────────────────────────

  typeVariant(t: GlAccountType): 'success' | 'warning' | 'neutral' | 'info' {
    const map: Record<GlAccountType, 'success' | 'warning' | 'neutral' | 'info'> = {
      ASSET:     'success',
      LIABILITY: 'warning',
      EQUITY:    'info',
      INCOME:    'success',
      EXPENSE:   'neutral',
    };
    return map[t];
  }

  private blankForm(): GlAccountRequest {
    return {
      glCode: '', name: '', accountType: 'ASSET', usage: 'DETAIL',
      manualEntriesAllowed: true, description: '', parentId: '',
    };
  }
}
