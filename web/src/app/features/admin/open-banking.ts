import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge';
import { AdminService, TppRegistration, RegisterTppRequest, TppStatus } from './admin.service';

const SCOPES = ['openid', 'accounts', 'payments', 'fundsconfirmations'];
type ModalType = 'create' | 'delete' | null;

@Component({
  selector: 'app-open-banking',
  standalone: true,
  imports: [CommonModule, FormsModule, StatusBadgeComponent],
  templateUrl: './open-banking.html',
  styleUrl: './open-banking.scss',
})
export class OpenBankingComponent implements OnInit {
  private readonly svc = inject(AdminService);

  tpps:    TppRegistration[] = [];
  loading  = true;
  error    = '';

  readonly availableScopes = SCOPES;

  // ── Modal ──────────────────────────────────────────────────────────────────
  activeModal:  ModalType = null;
  deletingId    = '';
  deletingName  = '';
  modalWorking  = false;
  modalError    = '';

  form: RegisterTppRequest = this.blankForm();

  ngOnInit(): void {
    this.svc.listTpps().subscribe({
      next: list => { this.tpps = list; this.loading = false; },
      error: () => { this.error = 'Failed to load TPP registrations.'; this.loading = false; },
    });
  }

  openCreateModal(): void {
    this.activeModal = 'create'; this.form = this.blankForm();
    this.modalWorking = false; this.modalError = '';
  }

  openDeleteModal(t: TppRegistration): void {
    this.activeModal = 'delete'; this.deletingId = t.id; this.deletingName = t.name;
    this.modalWorking = false; this.modalError = '';
  }

  toggleScope(s: string): void {
    this.form.allowedScopes = this.form.allowedScopes.includes(s)
      ? this.form.allowedScopes.filter(x => x !== s)
      : [...this.form.allowedScopes, s];
  }

  submitCreate(): void {
    if (!this.form.name || !this.form.clientId) return;
    this.modalWorking = true;
    this.svc.registerTpp(this.form).subscribe({
      next: t => { this.tpps = [...this.tpps, t]; this.modalWorking = false; this.activeModal = null; },
      error: () => { this.modalError = 'Failed to register TPP.'; this.modalWorking = false; },
    });
  }

  submitDelete(): void {
    this.modalWorking = true;
    this.svc.revokeTpp(this.deletingId).subscribe({
      next: updated => {
        this.tpps = this.tpps.map(t => t.id === updated.id ? updated : t);
        this.modalWorking = false; this.activeModal = null;
      },
      error: () => { this.modalError = 'Failed to revoke TPP.'; this.modalWorking = false; },
    });
  }

  activateTpp(t: TppRegistration): void {
    this.svc.activateTpp(t.id).subscribe({
      next: updated => { this.tpps = this.tpps.map(x => x.id === updated.id ? updated : x); },
    });
  }

  closeModal(): void { if (!this.modalWorking) this.activeModal = null; }

  statusVariant(s: TppStatus): 'success' | 'neutral' | 'warning' {
    return s === 'ACTIVE' ? 'success' : s === 'REVOKED' ? 'neutral' : 'warning';
  }

  private blankForm(): RegisterTppRequest {
    return { name: '', clientId: '', country: '', allowedScopes: [] };
  }
}
