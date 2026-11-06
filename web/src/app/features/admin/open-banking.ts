import { Component, inject, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge';
import { AdminService, TppOrganization, TppStatus } from './admin.service';

type ModalType = 'approve' | 'reject' | 'suspend' | 'reactivate' | null;

/**
 * TPP registry. A TPP is a partner organisation: it registers itself in the partner portal, so the
 * bank doesn't register TPPs here. Staff review production applications, and suspend or reactivate a
 * TPP — a suspended TPP's API keys and portal logins stop working at once; its consents stay.
 */
@Component({
  changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-open-banking',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, StatusBadgeComponent],
  templateUrl: './open-banking.html',
  styleUrl: './open-banking.scss',
})
export class OpenBankingComponent implements OnInit {
  private readonly svc = inject(AdminService);

  tpps:    TppOrganization[] = [];
  loading  = true;
  error    = '';

  activeModal: ModalType = null;
  selected: TppOrganization | null = null;
  reason = '';
  modalWorking = false;
  modalError = '';

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.svc.listTpps().subscribe({
      next: list => { this.tpps = list; this.loading = false; this.error = ''; },
      error: () => { this.error = 'Failed to load TPPs.'; this.loading = false; },
    });
  }

  open(modal: Exclude<ModalType, null>, t: TppOrganization): void {
    this.activeModal = modal; this.selected = t; this.reason = '';
    this.modalWorking = false; this.modalError = '';
  }

  closeModal(): void { if (!this.modalWorking) this.activeModal = null; }

  submit(): void {
    if (!this.selected || !this.activeModal) return;
    if (this.activeModal === 'suspend' && !this.reason.trim()) return;
    const id = this.selected.id;
    const call: Observable<unknown> = {
      approve: () => this.svc.approveTpp(id),
      reject: () => this.svc.rejectTpp(id),
      suspend: () => this.svc.suspendTpp(id, this.reason.trim()),
      reactivate: () => this.svc.reactivateTpp(id),
    }[this.activeModal]();
    this.modalWorking = true;
    call.subscribe({
      next: () => { this.modalWorking = false; this.activeModal = null; this.load(); },
      error: (e: HttpErrorResponse) => {
        this.modalError = e.error?.errors?.[0]?.message ?? 'The action failed.';
        this.modalWorking = false;
      },
    });
  }

  /** A production application is waiting for review. */
  isPendingReview(t: TppOrganization): boolean {
    return t.status === 'PENDING_REVIEW';
  }

  statusVariant(s: TppStatus): 'success' | 'warning' | 'info' | 'error' {
    switch (s) {
      case 'PRODUCTION': return 'success';
      case 'PENDING_REVIEW': return 'warning';
      case 'SUSPENDED': return 'error';
      default: return 'info';
    }
  }
}
