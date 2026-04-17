import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SystemService, SystemPaymentType, CreatePaymentTypeRequest } from './system.service';

@Component({
  selector: 'app-payment-types',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './payment-types.html',
  styleUrl: './payment-types.scss',
})
export class PaymentTypesComponent implements OnInit {
  private readonly svc = inject(SystemService);

  types: SystemPaymentType[] = [];
  loading = true;
  error   = '';

  page     = 0;
  pageSize = 20;
  total    = 0;

  // Modal state
  activeModal: 'create' | 'edit' | 'delete' | null = null;
  editTarget: SystemPaymentType | null = null;
  deleteTarget: SystemPaymentType | null = null;
  working    = false;
  modalError = '';

  form: CreatePaymentTypeRequest = this.blank();

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.error = '';
    this.svc.listPaymentTypes(this.page).subscribe({
      next: res => {
        this.types   = res.content;
        this.total   = res.totalElements;
        this.loading = false;
      },
      error: () => { this.error = 'Failed to load payment types.'; this.loading = false; },
    });
  }

  openCreate(): void {
    this.form = this.blank();
    this.modalError = '';
    this.working = false;
    this.editTarget = null;
    this.activeModal = 'create';
  }

  openEdit(t: SystemPaymentType): void {
    this.form = { name: t.name, description: t.description ?? '', cashPayment: t.cashPayment, position: t.position ?? 0 };
    this.editTarget = t;
    this.modalError = '';
    this.working = false;
    this.activeModal = 'edit';
  }

  openDelete(t: SystemPaymentType): void {
    this.deleteTarget = t;
    this.working = false;
    this.activeModal = 'delete';
  }

  closeModal(): void { this.activeModal = null; }

  save(): void {
    this.working = true;
    this.modalError = '';
    const obs = this.activeModal === 'edit' && this.editTarget
      ? this.svc.updatePaymentType(this.editTarget.id, this.form)
      : this.svc.createPaymentType(this.form);
    obs.subscribe({
      next: () => { this.closeModal(); this.load(); },
      error: () => { this.modalError = 'Save failed. Please try again.'; this.working = false; },
    });
  }

  confirmDelete(): void {
    if (!this.deleteTarget) return;
    this.working = true;
    this.svc.deletePaymentType(this.deleteTarget.id).subscribe({
      next: () => { this.closeModal(); this.load(); },
      error: () => { this.working = false; },
    });
  }

  get totalPages(): number { return Math.ceil(this.total / this.pageSize); }
  prev(): void { if (this.page > 0) { this.page--; this.load(); } }
  next(): void { if (this.page < this.totalPages - 1) { this.page++; this.load(); } }

  private blank(): CreatePaymentTypeRequest {
    return { name: '', description: '', cashPayment: false, position: 0 };
  }
}
