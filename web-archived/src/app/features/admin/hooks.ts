import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge';
import { AdminService, Hook, CreateHookRequest, HookType } from './admin.service';

type ModalType = 'create' | 'edit' | 'delete' | null;

const AVAILABLE_EVENTS = [
  'LOAN_APPROVED', 'LOAN_DISBURSED', 'LOAN_REPAYMENT', 'LOAN_ARREARS',
  'ACCOUNT_OPENED', 'ACCOUNT_CLOSED', 'LARGE_TRANSACTION',
  'CUSTOMER_CREATED', 'KYC_APPROVED', 'FAILED_LOGIN',
];

@Component({
  selector: 'app-hooks',
  standalone: true,
  imports: [CommonModule, FormsModule, StatusBadgeComponent],
  templateUrl: './hooks.html',
  styleUrl: './hooks.scss',
})
export class HooksComponent implements OnInit {
  private readonly svc = inject(AdminService);

  hooks:   Hook[] = [];
  loading  = true;
  error    = '';

  readonly hookTypes:      HookType[] = ['WEB', 'SMS'];
  readonly availableEvents             = AVAILABLE_EVENTS;

  // ── Modal ──────────────────────────────────────────────────────────────────
  activeModal:  ModalType = null;
  editingId     = '';
  deletingName  = '';
  modalWorking  = false;
  modalError    = '';

  formName:     string   = '';
  formType:     HookType = 'WEB';
  formUrl:      string   = '';
  formEvents:   string[] = [];

  ngOnInit(): void {
    this.svc.listHooks().subscribe({
      next: list => { this.hooks = list; this.loading = false; },
      error: () => { this.error = 'Failed to load hooks.'; this.loading = false; },
    });
  }

  openCreateModal(): void {
    this.activeModal = 'create'; this.editingId = '';
    this.formName = ''; this.formType = 'WEB'; this.formUrl = ''; this.formEvents = [];
    this.modalWorking = false; this.modalError = '';
  }

  openEditModal(h: Hook): void {
    this.activeModal = 'edit'; this.editingId = h.id;
    this.formName = h.name; this.formType = h.hookType; this.formUrl = h.url;
    this.formEvents = [...h.events]; this.modalWorking = false; this.modalError = '';
  }

  openDeleteModal(h: Hook): void {
    this.activeModal = 'delete'; this.editingId = h.id;
    this.deletingName = h.name; this.modalWorking = false; this.modalError = '';
  }

  toggleEvent(e: string): void {
    this.formEvents = this.formEvents.includes(e)
      ? this.formEvents.filter(x => x !== e)
      : [...this.formEvents, e];
  }

  submitModal(): void {
    if (!this.formName || !this.formUrl || this.formEvents.length === 0) return;
    this.modalWorking = true;
    const req: CreateHookRequest = {
      name: this.formName, hookType: this.formType, url: this.formUrl, events: this.formEvents,
    };
    const call = this.activeModal === 'create'
      ? this.svc.createHook(req) : this.svc.updateHook(this.editingId, req);
    call.subscribe({
      next: h => {
        this.hooks = this.activeModal === 'create'
          ? [...this.hooks, h] : this.hooks.map(x => x.id === h.id ? h : x);
        this.modalWorking = false; this.activeModal = null;
      },
      error: () => { this.modalError = 'Failed to save hook.'; this.modalWorking = false; },
    });
  }

  submitDelete(): void {
    this.modalWorking = true;
    this.svc.deleteHook(this.editingId).subscribe({
      next: () => {
        this.hooks = this.hooks.filter(h => h.id !== this.editingId);
        this.modalWorking = false; this.activeModal = null;
      },
      error: () => { this.modalError = 'Cannot delete hook.'; this.modalWorking = false; },
    });
  }

  closeModal(): void { if (!this.modalWorking) this.activeModal = null; }
}
