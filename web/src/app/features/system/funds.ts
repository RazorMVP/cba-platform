import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SystemService, Fund, CreateFundRequest } from './system.service';

@Component({
  selector: 'app-funds',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './funds.html',
  styleUrl: './funds.scss',
})
export class FundsComponent implements OnInit {
  private readonly svc = inject(SystemService);

  funds:   Fund[] = [];
  loading = true;
  error   = '';

  activeModal: 'create' | 'edit' | null = null;
  editTarget:  Fund | null = null;
  working    = false;
  modalError = '';

  form: CreateFundRequest = this.blank();

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.error = '';
    this.svc.listFunds().subscribe({
      next: list => { this.funds = list; this.loading = false; },
      error: () => { this.error = 'Failed to load funds.'; this.loading = false; },
    });
  }

  openCreate(): void {
    this.form = this.blank();
    this.editTarget = null;
    this.modalError = '';
    this.working = false;
    this.activeModal = 'create';
  }

  openEdit(f: Fund): void {
    this.form = { name: f.name, externalId: f.externalId ?? '' };
    this.editTarget = f;
    this.modalError = '';
    this.working = false;
    this.activeModal = 'edit';
  }

  closeModal(): void { this.activeModal = null; }

  save(): void {
    this.working = true;
    this.modalError = '';
    const obs = this.activeModal === 'edit' && this.editTarget
      ? this.svc.updateFund(this.editTarget.id, this.form)
      : this.svc.createFund(this.form);
    obs.subscribe({
      next: () => { this.closeModal(); this.load(); },
      error: () => { this.modalError = 'Save failed. Please try again.'; this.working = false; },
    });
  }

  private blank(): CreateFundRequest {
    return { name: '', externalId: '' };
  }
}
