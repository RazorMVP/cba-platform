import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  AccountingService, ProvisioningCriteria, ProvisioningCriteriaRequest,
  ProvisioningDefinition, GlAccount,
} from './accounting.service';

const DEFAULT_DEFINITIONS: ProvisioningDefinition[] = [
  { categoryName: 'STANDARD',     minAge: 0,   maxAge: 30,  provisionPercentage: 1,   liabilityAccountId: '', expenseAccountId: '' },
  { categoryName: 'WATCH',        minAge: 31,  maxAge: 90,  provisionPercentage: 5,   liabilityAccountId: '', expenseAccountId: '' },
  { categoryName: 'SUB_STANDARD', minAge: 91,  maxAge: 180, provisionPercentage: 25,  liabilityAccountId: '', expenseAccountId: '' },
  { categoryName: 'DOUBTFUL',     minAge: 181, maxAge: 360, provisionPercentage: 50,  liabilityAccountId: '', expenseAccountId: '' },
  { categoryName: 'LOSS',         minAge: 361, maxAge: 9999,provisionPercentage: 100, liabilityAccountId: '', expenseAccountId: '' },
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

  ngOnInit(): void {
    this.loadCriteria();
    this.svc.listGlAccounts().subscribe({
      next: list => { this.glAccounts = list; },
      error: () => {},
    });
  }

  loadCriteria(): void {
    this.svc.listProvisioningCriteria().subscribe({
      next: list => { this.criteria = list; this.loading = false; },
      error: () => { this.error = 'Failed to load provisioning criteria.'; this.loading = false; },
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
