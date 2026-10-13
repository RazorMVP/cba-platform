import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  AccountingService, ProvisioningCriteria, ProvisioningCriteriaRequest,
  ProvisioningDefinition, GlAccount, EclPdRate,
} from './accounting.service';

/** The API's own error text (envelope errors[0].message), or the fallback. */
function apiMessage(err: unknown, fallback: string): string {
  const e = err as { error?: { errors?: { message?: string }[] } };
  return e?.error?.errors?.[0]?.message ?? fallback;
}

/**
 * CBN Prudential Guidelines bands by days past due: a general provision on performing
 * loans, then sub-standard, doubtful and lost. The impairment job also provides 100% of
 * principal more than 90 days overdue on non-performing loans.
 */
const DEFAULT_DEFINITIONS: ProvisioningDefinition[] = [
  { categoryName: 'PERFORMING',   minAge: 0,   maxAge: 90,    provisionPercentage: 2,   liabilityAccountId: '', expenseAccountId: '' },
  { categoryName: 'SUB_STANDARD', minAge: 91,  maxAge: 180,   provisionPercentage: 10,  liabilityAccountId: '', expenseAccountId: '' },
  { categoryName: 'DOUBTFUL',     minAge: 181, maxAge: 360,   provisionPercentage: 50,  liabilityAccountId: '', expenseAccountId: '' },
  { categoryName: 'LOST',         minAge: 361, maxAge: 99999, provisionPercentage: 100, liabilityAccountId: '', expenseAccountId: '' },
];

type ModalType = 'create' | 'edit' | 'delete' | null;

@Component({
  selector: 'app-provisioning',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './provisioning.html',
  styleUrl: './provisioning.scss',
})
export class ProvisioningComponent implements OnInit {
  private readonly svc = inject(AccountingService);

  criteria: ProvisioningCriteria[] = [];
  loading = true;
  error   = '';

  // GL accounts for dropdowns
  glAccounts: GlAccount[] = [];
  get liabilityAccounts(): GlAccount[] {
    return this.glAccounts.filter(a => a.accountType === 'LIABILITY' && !a.disabled && a.usage === 'DETAIL');
  }
  get expenseAccounts(): GlAccount[] {
    return this.glAccounts.filter(a => a.accountType === 'EXPENSE' && !a.disabled && a.usage === 'DETAIL');
  }

  // ── Modal ──────────────────────────────────────────────────────────────────
  activeModal:   ModalType = null;
  editingId      = '';
  deletingName   = '';
  modalWorking   = false;
  modalError     = '';

  formName        = '';
  formDefinitions: ProvisioningDefinition[] = [];

  // ── ECL probability-of-default table ───────────────────────────────────────
  pdRates: EclPdRate[] = [];
  pdForm: EclPdRate[] = [];
  pdEditing = false;
  pdSaving  = false;
  pdError   = '';

  ngOnInit(): void {
    this.loadCriteria();
    this.loadPdRates();
    this.svc.listGlAccounts().subscribe({
      next: list => { this.glAccounts = list; },
    });
  }

  loadCriteria(): void {
    this.svc.listProvisioningCriteria().subscribe({
      next: list => { this.criteria = list; this.loading = false; },
      error: () => { this.error = 'Failed to load provisioning criteria.'; this.loading = false; },
    });
  }

  loadPdRates(): void {
    this.svc.listPdRates().subscribe({
      next: rates => { this.pdRates = rates; },
      error: () => { this.pdError = 'Failed to load the PD table.'; },
    });
  }

  editPdRates(): void {
    this.pdForm    = this.pdRates.map(r => ({ ...r }));
    this.pdEditing = true;
    this.pdError   = '';
  }

  cancelPdRates(): void {
    this.pdEditing = false;
    this.pdError   = '';
  }

  addPdBand(): void {
    this.pdForm = [...this.pdForm, { stage: 1, minDpd: 0, maxDpd: 0, pdPercentage: 0 }];
  }

  removePdBand(i: number): void {
    this.pdForm = this.pdForm.filter((_, idx) => idx !== i);
  }

  savePdRates(): void {
    this.pdSaving = true;
    this.pdError  = '';
    const rates = this.pdForm.map(r => ({ ...r, stage: Number(r.stage) as EclPdRate['stage'] }));
    this.svc.updatePdRates(rates).subscribe({
      next: saved => { this.pdRates = saved; this.pdEditing = false; this.pdSaving = false; },
      error: err => { this.pdError = apiMessage(err, 'Failed to save the PD table.'); this.pdSaving = false; },
    });
  }

  // ── Open modals ────────────────────────────────────────────────────────────

  openCreateModal(): void {
    this.activeModal      = 'create';
    this.editingId        = '';
    this.formName         = '';
    this.formDefinitions  = DEFAULT_DEFINITIONS.map(d => ({ ...d }));
    this.modalWorking     = false;
    this.modalError       = '';
  }

  openEditModal(c: ProvisioningCriteria): void {
    this.activeModal      = 'edit';
    this.editingId        = c.id;
    this.formName         = c.criteriaName;
    this.formDefinitions  = c.definitions.map(d => ({ ...d }));
    this.modalWorking     = false;
    this.modalError       = '';
  }

  openDeleteModal(c: ProvisioningCriteria): void {
    this.activeModal    = 'delete';
    this.editingId      = c.id;
    this.deletingName   = c.criteriaName;
    this.modalWorking   = false;
    this.modalError     = '';
  }

  // ── Submit actions ─────────────────────────────────────────────────────────

  addDefinition(): void {
    this.formDefinitions = [...this.formDefinitions, {
      categoryName: '', minAge: 0, maxAge: 0,
      provisionPercentage: 0, liabilityAccountId: '', expenseAccountId: '',
    }];
  }

  removeDefinition(i: number): void {
    this.formDefinitions = this.formDefinitions.filter((_, idx) => idx !== i);
  }

  submitModal(): void {
    if (!this.formName || this.formDefinitions.length === 0) return;
    this.modalWorking = true;
    const req: ProvisioningCriteriaRequest = {
      criteriaName: this.formName,
      definitions:  this.formDefinitions,
    };
    const call = this.activeModal === 'create'
      ? this.svc.createProvisioningCriteria(req)
      : this.svc.updateProvisioningCriteria(this.editingId, req);

    call.subscribe({
      next: c => {
        if (this.activeModal === 'create') {
          this.criteria = [...this.criteria, c];
        } else {
          this.criteria = this.criteria.map(x => x.id === c.id ? c : x);
        }
        this.modalWorking = false;
        this.activeModal  = null;
      },
      error: () => { this.modalError = 'Failed to save criteria.'; this.modalWorking = false; },
    });
  }

  submitDelete(): void {
    this.modalWorking = true;
    this.svc.deleteProvisioningCriteria(this.editingId).subscribe({
      next: () => {
        this.criteria     = this.criteria.filter(c => c.id !== this.editingId);
        this.modalWorking = false;
        this.activeModal  = null;
      },
      error: () => { this.modalError = 'Cannot delete criteria in use.'; this.modalWorking = false; },
    });
  }

  closeModal(): void { if (!this.modalWorking) this.activeModal = null; }

  // ── Helpers ────────────────────────────────────────────────────────────────

  definitionCount(c: ProvisioningCriteria): number {
    return c.definitions?.length ?? 0;
  }

  glAccountLabel(id: string): string {
    const a = this.glAccounts.find(x => x.id === id);
    return a ? `${a.glCode} ${a.name}` : id || '—';
  }
}
