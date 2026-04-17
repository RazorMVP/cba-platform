import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  SystemService, CreditBureau, CreditBureauMapping,
  CreateCreditBureauRequest, CreateCreditBureauMappingRequest,
} from './system.service';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge';

@Component({
  selector: 'app-credit-bureau',
  standalone: true,
  imports: [CommonModule, FormsModule, StatusBadgeComponent],
  templateUrl: './credit-bureau.html',
  styleUrl: './credit-bureau.scss',
})
export class CreditBureauComponent implements OnInit {
  private readonly svc = inject(SystemService);

  bureaus:  CreditBureau[] = [];
  loading = true;
  error   = '';

  expandedBureau: string | null = null;
  mappings:       Record<string, CreditBureauMapping[]> = {};
  mappingsLoading: Record<string, boolean> = {};

  activeModal: 'create' | 'edit' | 'delete' | 'toggle' | 'add-mapping' | null = null;
  editTarget:      CreditBureau | null = null;
  deleteTarget:    CreditBureau | null = null;
  toggleTarget:    CreditBureau | null = null;
  mappingBureauId: string | null = null;
  working    = false;
  modalError = '';

  form: CreateCreditBureauRequest = this.blank();
  mappingForm: CreateCreditBureauMappingRequest = { loanProductId: '', creditCheckMandatory: false };

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.error = '';
    this.svc.listCreditBureaus().subscribe({
      next: list => { this.bureaus = list; this.loading = false; },
      error: () => { this.error = 'Failed to load credit bureaus.'; this.loading = false; },
    });
  }

  toggleExpand(id: string): void {
    if (this.expandedBureau === id) {
      this.expandedBureau = null;
      return;
    }
    this.expandedBureau = id;
    if (!this.mappings[id]) { this.loadMappings(id); }
  }

  loadMappings(bureauId: string): void {
    this.mappingsLoading[bureauId] = true;
    this.svc.listCreditBureauMappings(bureauId).subscribe({
      next: list => { this.mappings[bureauId] = list; this.mappingsLoading[bureauId] = false; },
      error: () => { this.mappingsLoading[bureauId] = false; },
    });
  }

  openCreate(): void {
    this.form = this.blank();
    this.editTarget = null;
    this.modalError = '';
    this.working = false;
    this.activeModal = 'create';
  }

  openEdit(b: CreditBureau): void {
    this.form = { name: b.name, country: b.country, implClass: b.implClass, description: b.description ?? '' };
    this.editTarget = b;
    this.modalError = '';
    this.working = false;
    this.activeModal = 'edit';
  }

  openDelete(b: CreditBureau): void {
    this.deleteTarget = b;
    this.working = false;
    this.activeModal = 'delete';
  }

  openToggle(b: CreditBureau): void {
    this.toggleTarget = b;
    this.working = false;
    this.modalError = '';
    this.activeModal = 'toggle';
  }

  openAddMapping(bureauId: string): void {
    this.mappingBureauId = bureauId;
    this.mappingForm = { loanProductId: '', creditCheckMandatory: false };
    this.modalError = '';
    this.working = false;
    this.activeModal = 'add-mapping';
  }

  closeModal(): void { this.activeModal = null; }

  save(): void {
    this.working = true;
    this.modalError = '';
    const obs = this.activeModal === 'edit' && this.editTarget
      ? this.svc.updateCreditBureau(this.editTarget.id, this.form)
      : this.svc.createCreditBureau(this.form);
    obs.subscribe({
      next: () => { this.closeModal(); this.load(); },
      error: () => { this.modalError = 'Save failed. Please try again.'; this.working = false; },
    });
  }

  confirmDelete(): void {
    if (!this.deleteTarget) return;
    this.working = true;
    this.svc.deleteCreditBureau(this.deleteTarget.id).subscribe({
      next: () => { this.closeModal(); this.load(); },
      error: () => { this.working = false; },
    });
  }

  confirmToggle(): void {
    if (!this.toggleTarget) return;
    this.working = true;
    const obs = this.toggleTarget.active
      ? this.svc.deactivateCreditBureau(this.toggleTarget.id)
      : this.svc.activateCreditBureau(this.toggleTarget.id);
    obs.subscribe({
      next: () => { this.closeModal(); this.load(); },
      error: () => { this.modalError = 'Action failed. Please try again.'; this.working = false; },
    });
  }

  saveMapping(): void {
    if (!this.mappingBureauId) return;
    this.working = true;
    this.modalError = '';
    this.svc.createCreditBureauMapping(this.mappingBureauId, this.mappingForm).subscribe({
      next: () => {
        delete this.mappings[this.mappingBureauId!];
        this.loadMappings(this.mappingBureauId!);
        this.closeModal();
      },
      error: () => { this.modalError = 'Save failed. Please try again.'; this.working = false; },
    });
  }

  deleteMapping(bureauId: string, mappingId: string): void {
    this.svc.deleteCreditBureauMapping(bureauId, mappingId).subscribe({
      next: () => this.loadMappings(bureauId),
      error: () => { this.mappingsLoading[bureauId] = false; },
    });
  }

  private blank(): CreateCreditBureauRequest {
    return { name: '', country: '', implClass: '', description: '' };
  }
}
