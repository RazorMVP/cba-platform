import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService, Office, CreateOfficeRequest } from './admin.service';

type ModalType = 'create' | 'edit' | null;

@Component({
  selector: 'app-offices',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './offices.html',
  styleUrl: './offices.scss',
})
export class OfficesComponent implements OnInit {
  private readonly svc = inject(AdminService);

  offices: Office[] = [];
  loading = true;
  error   = '';

  searchQuery = '';
  get filtered(): Office[] {
    const q = this.searchQuery.toLowerCase();
    return q ? this.offices.filter(o => o.name.toLowerCase().includes(q)) : this.offices;
  }

  // ── Modal ──────────────────────────────────────────────────────────────────
  activeModal:  ModalType = null;
  editingId     = '';
  modalWorking  = false;
  modalError    = '';
  form: CreateOfficeRequest = this.blankForm();

  get parentOffices(): Office[] {
    return this.offices.filter(o => o.id !== this.editingId);
  }

  ngOnInit(): void {
    this.svc.listOffices().subscribe({
      next: list => { this.offices = list; this.loading = false; },
      error: () => { this.error = 'Failed to load offices.'; this.loading = false; },
    });
  }

  openCreateModal(): void {
    this.activeModal = 'create'; this.editingId = '';
    this.form = this.blankForm(); this.modalWorking = false; this.modalError = '';
  }

  openEditModal(o: Office): void {
    this.activeModal = 'edit'; this.editingId = o.id;
    this.form = {
      name: o.name, externalId: o.externalId, openingDate: o.openingDate,
      parentId: o.parentId ?? '',
    };
    this.modalWorking = false; this.modalError = '';
  }

  submitModal(): void {
    if (!this.form.name || !this.form.openingDate) return;
    this.modalWorking = true;
    const req: CreateOfficeRequest = {
      ...this.form, parentId: this.form.parentId || undefined,
    };
    const call = this.activeModal === 'create'
      ? this.svc.createOffice(req)
      : this.svc.updateOffice(this.editingId, req);
    call.subscribe({
      next: o => {
        this.offices = this.activeModal === 'create'
          ? [...this.offices, o]
          : this.offices.map(x => x.id === o.id ? o : x);
        this.modalWorking = false; this.activeModal = null;
      },
      error: () => { this.modalError = 'Failed to save office.'; this.modalWorking = false; },
    });
  }

  closeModal(): void { if (!this.modalWorking) this.activeModal = null; }

  parentName(o: Office): string {
    return o.parentName ?? '—';
  }

  private blankForm(): CreateOfficeRequest {
    return { name: '', externalId: '', openingDate: '', parentId: '' };
  }
}
